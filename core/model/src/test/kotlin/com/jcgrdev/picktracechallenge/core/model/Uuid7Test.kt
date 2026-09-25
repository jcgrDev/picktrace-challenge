package com.jcgrdev.picktracechallenge.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Random
import java.util.UUID

class Uuid7Test {

    private class MutableClock(var millis: Long) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun millis(): Long = millis
    }

    /** Always returns 0 from nextInt so the per-ms counter starts at 0 and overflow is reachable. */
    private class ZeroCounterRandom : Random(42) {
        override fun nextInt(bound: Int): Int = 0
    }

    private val t0 = Instant.parse("2025-06-10T08:32:00Z").toEpochMilli()

    private fun UUID.timestampMs(): Long = mostSignificantBits ushr 16

    @Test
    fun `version nibble is 7 and variant bits are 10`() {
        val id = Uuid7(MutableClock(t0)).next()
        assertEquals(7, id.version())
        assertEquals(2, id.variant())
    }

    @Test
    fun `top 48 bits are the epoch millis`() {
        val id = Uuid7(MutableClock(t0)).next()
        assertEquals(t0, id.timestampMs())
    }

    @Test
    fun `ten thousand ids in the same millisecond strictly increase`() {
        val generator = Uuid7(MutableClock(t0))
        val ids = List(10_000) { generator.next() }
        ids.zipWithNext().forEach { (a, b) -> assertTrue("$a !< $b", a < b) }
        assertEquals(10_000, ids.toSet().size)
    }

    @Test
    fun `a clock set backwards still gives increasing ids`() {
        val clock = MutableClock(t0)
        val generator = Uuid7(clock)
        val before = generator.next()
        clock.millis = t0 - 60_000
        val after = generator.next()
        assertTrue(before < after)
        assertEquals(t0, after.timestampMs())
    }

    @Test
    fun `counter overflow rolls the timestamp forward by one ms`() {
        val generator = Uuid7(MutableClock(t0), ZeroCounterRandom())
        // Counter runs 0..0xFFF (4096 ids) within t0; the next one must move to t0 + 1.
        val ids = List(4096) { generator.next() }
        assertTrue(ids.all { it.timestampMs() == t0 })
        val rolled = generator.next()
        assertEquals(t0 + 1, rolled.timestampMs())
        assertTrue(ids.last() < rolled)
    }

    // UUID.compareTo is signed per half, so compare the unsigned 128-bit value instead.
    private operator fun UUID.compareTo(other: UUID): Int {
        val high = java.lang.Long.compareUnsigned(mostSignificantBits, other.mostSignificantBits)
        return if (high != 0) high else java.lang.Long.compareUnsigned(leastSignificantBits, other.leastSignificantBits)
    }
}
