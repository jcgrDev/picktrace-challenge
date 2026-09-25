package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import retrofit2.Response
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface PushOutcome {
    /** A parseable 2xx. [missing] = sent ops in neither list (plan.md OQ2: handled as transport). */
    data class Delivered(val acked: Set<String>, val rejected: Map<String, String>, val missing: Set<String>) : PushOutcome

    /** No network, 5xx, empty or unparseable body. */
    data class Transport(val error: String) : PushOutcome

    /** 429. */
    data class RateLimited(val retryAfter: Duration) : PushOutcome

    /** Any other non-2xx (plan.md OQ3: fails the whole batch). */
    data class Refused(val code: Int) : PushOutcome
}

/** Maps one push call to exactly one outcome (contracts/sync-api.md "Client handling of push results"). */
class PushOutcomeClassifier @Inject constructor(private val config: SyncConfig) {

    fun classify(result: Result<Response<PushResponse>>, sentOpIds: List<String>, now: Instant): PushOutcome {
        val response = result.getOrElse { return PushOutcome.Transport(it.describe()) }
        val code = response.code()
        return when {
            response.isSuccessful -> {
                val body = response.body() ?: return PushOutcome.Transport("HTTP $code with an empty body")
                val sent = sentOpIds.toSet()
                val acked = body.acked.filterTo(linkedSetOf()) { it in sent }
                val rejected = body.rejected
                    .filter { it.opId in sent && it.opId !in acked }
                    .associate { it.opId to it.reason }
                PushOutcome.Delivered(acked, rejected, sent - acked - rejected.keys)
            }
            code == 429 -> PushOutcome.RateLimited(retryAfter(response.headers()["Retry-After"], now))
            code in 500..599 -> PushOutcome.Transport("HTTP $code")
            else -> PushOutcome.Refused(code)
        }
    }

    /** Delta-seconds or HTTP-date; missing or unparseable falls back to the configured default. */
    private fun retryAfter(header: String?, now: Instant): Duration {
        val value = header?.trim() ?: return config.defaultRetryAfter
        value.toLongOrNull()?.let { if (it >= 0) return it.seconds }
        val date = runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: return config.defaultRetryAfter
        return (date.toEpochMilli() - now.toEpochMilli()).coerceAtLeast(0).milliseconds
    }

    private fun Throwable.describe(): String = "${this::class.simpleName}: ${message.orEmpty()}".trimEnd(' ', ':')
}
