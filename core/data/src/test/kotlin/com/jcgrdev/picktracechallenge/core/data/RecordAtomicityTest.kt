package com.jcgrdev.picktracechallenge.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.fixtures.sampleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Principle II: the event row and its CREATE op are written together or not at all. */
@RunWith(AndroidJUnit4::class)
class RecordAtomicityTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())

    @After
    fun close() = db.close()

    @Test
    fun opInsertFailureRollsBackTheEventInsert() = runTest {
        val firstEvent = UUID(0, 1)
        val firstOp = UUID(0, 2)
        val secondEvent = UUID(0, 3)
        // Second record reuses firstOp as its opId, so the op insert hits the unique index
        // after the event row was already inserted in the same transaction.
        val ids = ArrayDeque(listOf(firstEvent, firstOp, secondEvent, firstOp))
        val scheduler = FakeSyncScheduler()
        val repository = repositoryFor(db, scheduler, ids = IdGenerator { ids.removeFirst() })

        repository.record(sampleDraft())
        val failure = runCatching { repository.record(sampleDraft(quantity = "7")) }

        assertTrue(failure.isFailure)
        assertNull(db.fieldEventDao().getById(secondEvent.toString()))
        assertEquals(listOf(firstEvent), repository.observeEvents().first().map { it.id })
        assertEquals(1, db.pendingOpDao().count())
        assertEquals(1, scheduler.requests.size)
    }
}
