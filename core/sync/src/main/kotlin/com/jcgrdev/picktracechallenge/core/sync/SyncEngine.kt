package com.jcgrdev.picktracechallenge.core.sync

import androidx.room.withTransaction
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.database.entity.RunOutcome
import com.jcgrdev.picktracechallenge.core.database.entity.SyncRunEntity
import com.jcgrdev.picktracechallenge.core.model.FailureKind
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.network.SyncApi
import com.jcgrdev.picktracechallenge.core.network.dto.EntityType
import com.jcgrdev.picktracechallenge.core.network.dto.OpType
import com.jcgrdev.picktracechallenge.core.network.dto.PendingOpDto
import com.jcgrdev.picktracechallenge.core.network.dto.PushRequest
import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import retrofit2.Response
import java.time.Clock
import javax.inject.Inject

/**
 * Pushes the outbox in `seq` order, one batch at a time (data-model.md "Transactions").
 *
 * Every state change is a Room transaction, so a process kill at any point leaves a state the next
 * run can resume from: ops left IN_FLIGHT and runs left RUNNING are reset when the next run starts.
 * Nothing about a run survives in memory except [lastClaimedSeq], which only stops this run from
 * re-pushing ops it already tried.
 */
class SyncEngine @Inject constructor(
    private val db: PicktraceDatabase,
    private val api: SyncApi,
    private val json: Json,
    private val classifier: PushOutcomeClassifier,
    private val config: SyncConfig,
    private val clock: Clock,
) : SyncRunner {
    private val pendingOpDao = db.pendingOpDao()
    private val fieldEventDao = db.fieldEventDao()
    private val syncRunDao = db.syncRunDao()

    override suspend fun run(): RunResult {
        val runId = startRun()
        var lastClaimedSeq = 0L
        var deferred = 0
        while (true) {
            val batch = claimBatch(lastClaimedSeq)
            if (batch.isEmpty()) break
            lastClaimedSeq = batch.last().seq

            val outcome = classifier.classify(push(batch), batch.map { it.opId }, clock.instant())
            applyOutcome(runId, batch, outcome)

            when (outcome) {
                is PushOutcome.Delivered -> deferred += outcome.missing.size
                is PushOutcome.Transport -> return finishRun(runId, RunOutcome.TRANSPORT_ERROR, outcome.error, RunResult.Retry)
                is PushOutcome.RateLimited ->
                    return finishRun(runId, RunOutcome.RATE_LIMITED, "HTTP 429", RunResult.RetryAfter(outcome.retryAfter))
                is PushOutcome.Refused -> return finishRun(runId, RunOutcome.REFUSED, "HTTP ${outcome.code}", RunResult.Refused)
            }
        }
        return if (deferred > 0) {
            finishRun(runId, RunOutcome.TRANSPORT_ERROR, "$deferred ops missing from the server response", RunResult.Retry)
        } else {
            finishRun(runId, RunOutcome.COMPLETED, null, RunResult.Completed)
        }
    }

    private suspend fun startRun(): Long = db.withTransaction {
        val now = clock.millis()
        pendingOpDao.resetInFlight()
        syncRunDao.markRunningAsInterrupted(now)
        val id = syncRunDao.insert(SyncRunEntity(startedAtMs = now, outcome = RunOutcome.RUNNING))
        syncRunDao.trimTo(KEPT_RUNS)
        id
    }

    private suspend fun claimBatch(afterSeq: Long): List<PendingOpEntity> = db.withTransaction {
        val batch = pendingOpDao.queuedBatch(afterSeq, config.effectiveBatchSize)
        if (batch.isNotEmpty()) pendingOpDao.setState(batch.map { it.seq }, OpState.IN_FLIGHT)
        batch
    }

    /** Not `runCatching`: cancellation must propagate so a killed run leaves its batch IN_FLIGHT. */
    private suspend fun push(batch: List<PendingOpEntity>): Result<Response<PushResponse>> = try {
        Result.success(api.push(PushRequest(batch.map { it.toDto() })))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun applyOutcome(runId: Long, batch: List<PendingOpEntity>, outcome: PushOutcome) {
        db.withTransaction {
            val byOpId = batch.associateBy { it.opId }
            var acked = 0
            var rejected = 0
            var error: String? = null
            when (outcome) {
                is PushOutcome.Delivered -> {
                    // Principle II: the ack and the outbox delete commit together.
                    pendingOpDao.deleteByOpIds(outcome.acked)
                    fieldEventDao.setStatus(outcome.acked.map { byOpId.getValue(it).entityId }, SyncStatus.SYNCED)
                    outcome.rejected.forEach { (opId, reason) -> fail(byOpId.getValue(opId), FailureKind.REJECTED, reason) }
                    val missing = outcome.missing.map(byOpId::getValue)
                    if (missing.isNotEmpty()) transportFailure(missing, "Missing from the server response")
                    acked = outcome.acked.size
                    rejected = outcome.rejected.size
                }
                is PushOutcome.Transport -> transportFailure(batch, outcome.error).also { error = outcome.error }
                is PushOutcome.RateLimited -> transportFailure(batch, "HTTP 429").also { error = "HTTP 429" }
                is PushOutcome.Refused -> {
                    error = "HTTP ${outcome.code}"
                    batch.forEach { fail(it, FailureKind.REFUSED, "HTTP ${outcome.code}") }
                }
            }
            val run = syncRunDao.getById(runId)
            syncRunDao.update(
                run.copy(
                    batches = run.batches + 1,
                    acked = run.acked + acked,
                    rejected = run.rejected + rejected,
                    lastError = error ?: run.lastError,
                ),
            )
        }
    }

    /** Caller holds the transaction. Each op pays one attempt; ops out of attempts become FAILED. */
    private suspend fun transportFailure(ops: List<PendingOpEntity>, error: String) {
        pendingOpDao.recordTransportFailure(ops.map { it.seq }, error)
        ops.filter { it.attempts + 1 >= config.maxTransportAttempts }.forEach {
            fail(it, FailureKind.EXHAUSTED, "Could not reach server after ${config.maxTransportAttempts} attempts: $error")
        }
    }

    /** Caller holds the transaction. */
    private suspend fun fail(op: PendingOpEntity, kind: FailureKind, reason: String) {
        pendingOpDao.markFailed(op.seq, kind, reason)
        fieldEventDao.setStatus(listOf(op.entityId), SyncStatus.FAILED)
    }

    private suspend fun finishRun(runId: Long, outcome: RunOutcome, error: String?, result: RunResult): RunResult {
        db.withTransaction {
            val run = syncRunDao.getById(runId)
            syncRunDao.update(run.copy(outcome = outcome, finishedAtMs = clock.millis(), lastError = error ?: run.lastError))
        }
        return result
    }

    private fun PendingOpEntity.toDto() = PendingOpDto(
        opId = opId,
        entityType = EntityType.valueOf(entityType),
        entityId = entityId,
        opType = OpType.valueOf(opType),
        schemaVersion = schemaVersion,
        hlc = hlc,
        baseVersion = baseVersion,
        fields = json.parseToJsonElement(fieldsJson).jsonObject,
    )

    private companion object {
        const val KEPT_RUNS = 20
    }
}
