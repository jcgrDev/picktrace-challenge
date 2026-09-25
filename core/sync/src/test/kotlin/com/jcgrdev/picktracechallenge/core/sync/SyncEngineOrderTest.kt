package com.jcgrdev.picktracechallenge.core.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.TestOutbox
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** SC-004 and the clock-skew edge case: delivery follows recording order, never event timestamps. */
@RunWith(AndroidJUnit4::class)
class SyncEngineOrderTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val server = FakeSyncServer()

    @After
    fun close() = db.close()

    @Test
    fun deliveryFollowsRecordingOrderNotTimestamps() = runTest {
        val recorded = listOf("08:35", "08:30", "08:32").map {
            TestOutbox.record(db, timestamp = Instant.parse("2025-06-10T$it:00Z"))
        }

        engineFor(db, server, SyncConfig(batchSize = 2)).run()

        assertEquals(recorded.map { it.id.toString() }, server.deliveryLog.map { it.entityId })
        assertEquals(listOf("2025-06-10T08:35:00Z", "2025-06-10T08:30:00Z", "2025-06-10T08:32:00Z"),
            server.deliveryLog.map { it.fields!!.getValue("timestamp").toString().trim('"') })
    }
}
