package com.jcgrdev.picktracechallenge.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.fixtures.sampleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** SC-001: events recorded offline are present, unchanged, after the app is killed and relaunched. */
@RunWith(AndroidJUnit4::class)
class RelaunchPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun recordedEventsSurviveReopeningTheDatabase() = runTest {
        val name = "relaunch-${System.nanoTime()}.db"
        val before = TestDatabases.fileBacked(context, name)
        val beforeRepository = repositoryFor(before)
        listOf(
            sampleDraft(workerId = "w_001", blockId = "block_42", quantity = "3", timestamp = Instant.parse("2025-06-10T08:30:00Z")),
            sampleDraft(workerId = "w_002", blockId = "block_7", quantity = "12", timestamp = Instant.parse("2025-06-10T08:32:00Z")),
            sampleDraft(workerId = "w_001", blockId = "block_43", quantity = "1", timestamp = Instant.parse("2025-06-10T08:35:00Z")),
        ).forEach { beforeRepository.record(it) }
        val recorded = beforeRepository.observeEvents().first()
        before.close()

        val after = TestDatabases.fileBacked(context, name)
        val reloaded = repositoryFor(after).observeEvents().first()

        assertEquals(3, reloaded.size)
        assertEquals(recorded, reloaded)
        assertTrue(reloaded.all { it.status == SyncStatus.PENDING })
        assertEquals(3, after.pendingOpDao().count())
        after.close()
    }
}
