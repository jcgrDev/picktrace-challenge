package com.jcgrdev.picktracechallenge.core.model

/**
 * Hybrid logical clock value. Encoded as `"%019d-%04d-%s"` so that string order equals
 * (wallMs, counter) order (research §R7). Persistence lives in `:core:sync` `HlcClock`.
 */
data class Hlc(val wallMs: Long, val counter: Int, val nodeId: String) {

    fun encode(): String = "%019d-%04d-%s".format(wallMs, counter, nodeId)

    companion object {
        /** Largest counter that fits the four-digit field; the next tick carries into [wallMs]. */
        const val MAX_COUNTER = 9_999

        fun tick(last: Hlc, nowMs: Long): Hlc = when {
            nowMs > last.wallMs -> Hlc(nowMs, 0, last.nodeId)
            last.counter >= MAX_COUNTER -> Hlc(last.wallMs + 1, 0, last.nodeId)
            else -> Hlc(last.wallMs, last.counter + 1, last.nodeId)
        }

        fun parse(encoded: String): Hlc {
            val parts = encoded.split('-', limit = 3)
            require(parts.size == 3) { "Not an HLC: $encoded" }
            return Hlc(parts[0].toLong(), parts[1].toInt(), parts[2])
        }
    }
}
