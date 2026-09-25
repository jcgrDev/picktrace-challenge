package com.jcgrdev.picktracechallenge.core.sync

import android.content.Context
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.model.Hlc
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class HlcClockTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A frozen clock: only persisted state can keep ticks increasing. */
    private val frozen = Clock.fixed(Instant.parse("2025-06-10T08:32:00Z"), ZoneOffset.UTC)

    private suspend fun PicktraceDatabase.tick(clock: Clock = frozen): Hlc =
        withTransaction { HlcClock(syncStateDao(), clock).tick() }

    @Test
    fun nodeIdIsSixteenHexCharsAfterFirstOpen() = runTest {
        val db = TestDatabases.inMemory(context)
        val nodeId = db.syncStateDao().get().nodeId
        assertTrue(nodeId, nodeId.matches(Regex("^[0-9a-f]{16}$")))
        db.close()
    }

    @Test
    fun eachTickIsStrictlyGreater() = runTest {
        val db = TestDatabases.inMemory(context)
        val ticks = List(50) { db.tick() }
        ticks.zipWithNext().forEach { (a, b) -> assertTrue("${a.encode()} !< ${b.encode()}", a.encode() < b.encode()) }
        db.close()
    }

    @Test
    fun tickStatePersistsAcrossReopenAndNodeIdIsStable() = runTest {
        val name = "hlc-${System.nanoTime()}.db"
        val first = TestDatabases.fileBacked(context, name)
        val nodeBefore = first.syncStateDao().get().nodeId
        val beforeClose = List(3) { first.tick() }.last()
        first.close()

        val reopened = TestDatabases.fileBacked(context, name)
        val afterReopen = reopened.tick()
        assertTrue(beforeClose.encode() < afterReopen.encode())
        assertEquals(nodeBefore, reopened.syncStateDao().get().nodeId)
        assertEquals(nodeBefore, afterReopen.nodeId)
        reopened.close()
    }
}
