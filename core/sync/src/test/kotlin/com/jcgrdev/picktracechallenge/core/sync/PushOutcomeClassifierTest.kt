package com.jcgrdev.picktracechallenge.core.sync

import com.jcgrdev.picktracechallenge.core.network.dto.PushResponse
import com.jcgrdev.picktracechallenge.core.network.dto.RejectedOp
import kotlinx.serialization.SerializationException
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/** One test per row of contracts/sync-api.md "Client handling of push results". */
class PushOutcomeClassifierTest {

    private val classifier = PushOutcomeClassifier(SyncConfig())
    private val now = Instant.parse("2026-09-25T12:00:00Z")
    private val sent = listOf("a", "b", "c")

    private fun ok(body: PushResponse?): Result<Response<PushResponse>> = Result.success(Response.success(body))

    private fun error(code: Int, retryAfter: String? = null): Result<Response<PushResponse>> {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://fake.sync.local/sync/push").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .apply { if (retryAfter != null) header("Retry-After", retryAfter) }
            .body("".toResponseBody())
            .build()
        return Result.success(Response.error("".toResponseBody(), raw))
    }

    private fun classify(result: Result<Response<PushResponse>>) = classifier.classify(result, sent, now)

    @Test
    fun ackedRejectedAndMissingAreSplitPerOp() {
        val outcome = classify(ok(PushResponse(acked = listOf("a"), rejected = listOf(RejectedOp("b", "bad quantity")))))
        assertEquals(PushOutcome.Delivered(acked = setOf("a"), rejected = mapOf("b" to "bad quantity"), missing = setOf("c")), outcome)
    }

    @Test
    fun unknownOpIdsInTheResponseAreIgnored() {
        val outcome = classify(ok(PushResponse(acked = listOf("a", "b", "c", "zzz"), rejected = listOf(RejectedOp("yyy", "?")))))
        assertEquals(PushOutcome.Delivered(setOf("a", "b", "c"), emptyMap(), emptySet()), outcome)
    }

    @Test
    fun unparseableBodyIsTransport() {
        val outcome = classify(Result.failure(SerializationException("Unexpected JSON token")))
        assertEquals(true, outcome is PushOutcome.Transport)
    }

    @Test
    fun emptySuccessBodyIsTransport() {
        assertEquals(true, classify(ok(null)) is PushOutcome.Transport)
    }

    @Test
    fun ioExceptionIsTransport() {
        assertEquals(PushOutcome.Transport("IOException: connection reset"), classify(Result.failure(IOException("connection reset"))))
    }

    @Test
    fun fiveHundredsAreTransport() {
        assertEquals(PushOutcome.Transport("HTTP 503"), classify(error(503)))
        assertEquals(PushOutcome.Transport("HTTP 500"), classify(error(500)))
    }

    @Test
    fun tooManyRequestsUsesDeltaSecondsRetryAfter() {
        assertEquals(PushOutcome.RateLimited(7.seconds), classify(error(429, "7")))
    }

    @Test
    fun tooManyRequestsUsesHttpDateRetryAfter() {
        assertEquals(PushOutcome.RateLimited(90.seconds), classify(error(429, "Fri, 25 Sep 2026 12:01:30 GMT")))
    }

    @Test
    fun tooManyRequestsWithMissingOrGarbageRetryAfterFallsBackTo30s() {
        assertEquals(PushOutcome.RateLimited(30.seconds), classify(error(429)))
        assertEquals(PushOutcome.RateLimited(30.seconds), classify(error(429, "soon")))
    }

    @Test
    fun otherClientErrorsAreRefused() {
        assertEquals(PushOutcome.Refused(400), classify(error(400)))
        assertEquals(PushOutcome.Refused(401), classify(error(401)))
        assertEquals(PushOutcome.Refused(413), classify(error(413)))
    }
}
