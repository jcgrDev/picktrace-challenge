package com.jcgrdev.picktracechallenge.core.model

import java.security.SecureRandom
import java.time.Clock
import java.util.Random
import java.util.UUID

/**
 * UUIDv7 per RFC 9562 §5.7, monotonic within this instance via the 12-bit `rand_a` counter
 * (§6.2, method 1). If the clock goes backwards the last timestamp is reused; if the counter
 * overflows the timestamp moves forward by 1 ms. State is in memory on purpose: ids need to be
 * unique, and delivery order comes from `pending_op.seq`, not from ids (research §R6).
 */
class Uuid7(
    private val clock: Clock = Clock.systemUTC(),
    private val random: Random = SecureRandom(),
) : IdGenerator {

    private var lastMs = Long.MIN_VALUE
    private var counter = 0

    @Synchronized
    override fun next(): UUID {
        val now = clock.millis()
        if (now > lastMs) {
            lastMs = now
            counter = seedCounter()
        } else {
            counter++
            if (counter > MAX_COUNTER) {
                lastMs++
                counter = seedCounter()
            }
        }
        val msb = (lastMs shl 16) or (VERSION shl 12) or counter.toLong()
        val lsb = (random.nextLong() and RAND_B_MASK) or VARIANT_BITS
        return UUID(msb, lsb)
    }

    /** Random start in the lower half, leaving room to count up within the millisecond. */
    private fun seedCounter(): Int = random.nextInt(COUNTER_SEED_BOUND)

    private companion object {
        const val VERSION = 0x7L
        const val MAX_COUNTER = 0xFFF
        const val COUNTER_SEED_BOUND = 0x800
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        const val VARIANT_BITS = Long.MIN_VALUE // 0b10 in the top two bits
    }
}
