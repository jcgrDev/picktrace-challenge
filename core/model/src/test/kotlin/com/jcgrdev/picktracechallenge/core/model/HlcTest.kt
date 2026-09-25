package com.jcgrdev.picktracechallenge.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlcTest {

    private val node = "3f9a1c0e7b2d4a61"

    @Test
    fun `encode matches the agreed sortable format`() {
        val encoded = Hlc(1_757_800_000_000, 3, node).encode()
        assertTrue(encoded.matches(Regex("""^\d{19}-\d{4}-[0-9a-f]{16}$""")))
        assertEquals("0000001757800000000-0003-3f9a1c0e7b2d4a61", encoded)
    }

    @Test
    fun `string order equals wall then counter order`() {
        val clocks = listOf(
            Hlc(5, 0, node), Hlc(5, 1, node), Hlc(5, 10, node), Hlc(6, 0, node), Hlc(100, 2, node),
            Hlc(1_757_800_000_000, 9_999, node),
        )
        assertEquals(clocks.map { it.encode() }, clocks.map { it.encode() }.sorted())
    }

    @Test
    fun `tick with now ahead of last resets the counter`() {
        val next = Hlc.tick(Hlc(100, 7, node), nowMs = 200)
        assertEquals(Hlc(200, 0, node), next)
    }

    @Test
    fun `tick with now equal to last increments the counter`() {
        val next = Hlc.tick(Hlc(100, 7, node), nowMs = 100)
        assertEquals(Hlc(100, 8, node), next)
    }

    @Test
    fun `tick with now behind last keeps the last wall and increments the counter`() {
        val next = Hlc.tick(Hlc(100, 7, node), nowMs = 50)
        assertEquals(Hlc(100, 8, node), next)
    }

    @Test
    fun `tick at the four-digit counter limit carries into the wall time`() {
        val next = Hlc.tick(Hlc(100, Hlc.MAX_COUNTER, node), nowMs = 100)
        assertEquals(Hlc(101, 0, node), next)
        assertTrue(Hlc(100, Hlc.MAX_COUNTER, node).encode() < next.encode())
    }

    @Test
    fun `parse is the inverse of encode`() {
        val hlc = Hlc(1_757_800_000_123, 42, node)
        assertEquals(hlc, Hlc.parse(hlc.encode()))
    }
}
