package com.jcgrdev.picktracechallenge.core.testing

import com.jcgrdev.picktracechallenge.core.network.SyncApi
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.network.dto.ChangeDto
import com.jcgrdev.picktracechallenge.core.network.dto.OpType
import com.jcgrdev.picktracechallenge.core.network.dto.PendingOpDto
import com.jcgrdev.picktracechallenge.core.network.dto.PullResponse
import com.jcgrdev.picktracechallenge.core.network.dto.PushRequest
import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import com.jcgrdev.picktracechallenge.core.network.dto.RejectedOp
import com.jcgrdev.picktracechallenge.core.network.dto.SnapshotResponse
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

/**
 * In-memory implementation of the sync contract as an OkHttp application interceptor
 * (contracts/fake-sync-server.md). Requests to the `/sync/` paths never reach the network; other paths get 404.
 * State lives in the instance: share one instance across engines to simulate an app relaunch.
 */
class FakeSyncServer(private val json: Json = SyncJson) : Interceptor {

    sealed interface Fault {
        /** No fault; lets a test target a later push. */
        data object Pass : Fault
        data object DropConnection : Fault
        data object DropAfterProcessing : Fault
        data class Status(val code: Int, val retryAfter: String? = null) : Fault
        data object GarbageBody : Fault
        data class PartialBody(val omitOpIds: Set<String>) : Fault
    }

    private class RejectRule(val reason: String, val predicate: (PendingOpDto) -> Boolean)

    private val lock = Any()
    private val _deliveryLog = mutableListOf<PendingOpDto>()
    private val _pushes = mutableListOf<List<String>>()
    private val _ackedOpIds = linkedSetOf<String>()
    private val entities = linkedMapOf<String, ChangeDto>()
    private val changeLog = mutableListOf<Pair<Long, ChangeDto>>()
    private val faults = ArrayDeque<Fault>()
    private val rejectRules = mutableListOf<RejectRule>()
    private var cursor = 0L

    /** Pull cursors below this get `410 Gone`. */
    @Volatile var expiredBefore: Long = 0

    /** Every op the server processed, in order, including repeats. */
    val deliveryLog: List<PendingOpDto> get() = synchronized(lock) { _deliveryLog.toList() }
    /** opIds of every push request received, faulted or not: the batch shape the client sent. */
    val pushes: List<List<String>> get() = synchronized(lock) { _pushes.toList() }
    val ackedOpIds: Set<String> get() = synchronized(lock) { _ackedOpIds.toSet() }

    /** Latest stored state; `version` is how many times the entity was stored. */
    fun storedEntity(entityId: String): ChangeDto? = synchronized(lock) { entities[entityId] }

    fun rejectWhen(reason: String, predicate: (PendingOpDto) -> Boolean) {
        synchronized(lock) { rejectRules += RejectRule(reason, predicate) }
    }

    fun enqueueFault(fault: Fault) {
        synchronized(lock) { faults.addLast(fault) }
    }

    fun reset() = synchronized(lock) {
        _deliveryLog.clear(); _pushes.clear(); _ackedOpIds.clear(); entities.clear(); changeLog.clear()
        faults.clear(); rejectRules.clear(); cursor = 0; expiredBefore = 0
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        return when {
            request.method == "POST" && path == "/sync/push" -> push(request)
            request.method == "GET" && path == "/sync/pull" -> pull(request)
            request.method == "GET" && path == "/sync/snapshot" -> snapshot(request)
            else -> respond(request, 404, "{}")
        }
    }

    private fun push(request: Request): Response {
        val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
        val pushRequest = runCatching { json.decodeFromString<PushRequest>(body) }.getOrNull()
        val fault = synchronized(lock) {
            if (pushRequest != null) _pushes += pushRequest.ops.map { it.opId }
            faults.removeFirstOrNull()
        }
        when (fault) {
            Fault.DropConnection -> throw IOException("FakeSyncServer: connection dropped")
            is Fault.Status -> return respond(request, fault.code, "{}", fault.retryAfter)
            Fault.GarbageBody -> return respond(request, 200, "<html>definitely not json</html>")
            else -> Unit
        }
        if (pushRequest == null || pushRequest.ops.size > PushRequest.MAX_OPS) return respond(request, 400, "{}")

        val response = synchronized(lock) { process(pushRequest.ops) }
        return when (fault) {
            Fault.DropAfterProcessing -> throw IOException("FakeSyncServer: dropped after processing")
            is Fault.PartialBody -> respond(
                request,
                200,
                json.encodeToString(
                    PushResponse(
                        acked = response.acked - fault.omitOpIds,
                        rejected = response.rejected.filterNot { it.opId in fault.omitOpIds },
                    ),
                ),
            )
            else -> respond(request, 200, json.encodeToString(response))
        }
    }

    /** Caller holds [lock]. */
    private fun process(ops: List<PendingOpDto>): PushResponse {
        _deliveryLog += ops
        val acked = mutableListOf<String>()
        val rejected = mutableListOf<RejectedOp>()
        for (op in ops) {
            if (op.opId in _ackedOpIds) {
                acked += op.opId // idempotent by opId
                continue
            }
            val rule = rejectRules.firstOrNull { it.predicate(op) }
            if (rule != null) {
                rejected += RejectedOp(op.opId, rule.reason, entities[op.entityId]?.version)
                continue
            }
            cursor++
            val stored = ChangeDto(
                entityType = op.entityType,
                entityId = op.entityId,
                version = (entities[op.entityId]?.version ?: 0) + 1,
                hlc = op.hlc,
                deleted = op.opType == OpType.DELETE,
                fields = if (op.opType == OpType.DELETE) null else op.fields,
            )
            entities[op.entityId] = stored
            changeLog += cursor to stored
            _ackedOpIds += op.opId
            acked += op.opId
        }
        return PushResponse(acked, rejected)
    }

    private fun pull(request: Request): Response {
        val from = request.url.queryParameter("cursor")?.toLongOrNull() ?: 0
        val limit = request.url.queryParameter("limit")?.toIntOrNull() ?: 100
        if (from < expiredBefore) return respond(request, 410, "{}")
        val page = synchronized(lock) { changeLog.filter { it.first > from } }
        val slice = page.take(limit)
        val body = PullResponse(
            changes = slice.map { it.second },
            nextCursor = slice.lastOrNull()?.first ?: from,
            hasMore = page.size > slice.size,
        )
        return respond(request, 200, json.encodeToString(body))
    }

    private fun snapshot(request: Request): Response {
        val page = request.url.queryParameter("page")?.toIntOrNull() ?: 0
        val (live, current) = synchronized(lock) { entities.values.filterNot { it.deleted } to cursor }
        val chunk = live.drop(page * SNAPSHOT_PAGE_SIZE).take(SNAPSHOT_PAGE_SIZE)
        val hasMore = live.size > (page + 1) * SNAPSHOT_PAGE_SIZE
        return respond(request, 200, json.encodeToString(SnapshotResponse(chunk, page, hasMore, current)))
    }

    private fun respond(request: Request, code: Int, body: String, retryAfter: String? = null): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
            .body(body.toResponseBody(JSON))
            .build()

    private companion object {
        const val SNAPSHOT_PAGE_SIZE = 100
        val JSON = "application/json".toMediaType()
    }
}

/**
 * A real Retrofit [SyncApi] whose calls are served by this fake. [before] interceptors run first
 * (e.g. a gate that holds a push so a test can cancel mid-flight).
 */
fun FakeSyncServer.syncApi(vararg before: Interceptor): SyncApi {
    val client = OkHttpClient.Builder()
        .apply { before.forEach(::addInterceptor) }
        .addInterceptor(this)
        .build()
    return Retrofit.Builder()
        .baseUrl("https://fake.sync.local/")
        .client(client)
        .addConverterFactory(SyncJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SyncApi::class.java)
}
