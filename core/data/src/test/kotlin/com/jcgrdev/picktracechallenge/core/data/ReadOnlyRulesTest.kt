package com.jcgrdev.picktracechallenge.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.fixtures.sampleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** US4-5, FR-009a, Principle IV: synced and in-flight events are read-only, and a refusal changes nothing. */
@RunWith(AndroidJUnit4::class)
class ReadOnlyRulesTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = repositoryFor(db)

    @After
    fun close() = db.close()

    private suspend fun record(): UUID = (repository.record(sampleDraft()) as RecordResult.Recorded).id

    @Test
    fun syncedEventsCannotBeEditedOrDeleted() = runTest {
        val id = record()
        db.syncWith(FakeSyncServer())
        assertEquals(SyncStatus.SYNCED, repository.observeEvent(id).first()!!.event.status)
        val before = db.snapshot()

        assertEquals(MutationResult.ReadOnlySynced, repository.updateQuantity(id, "9"))
        assertEquals(MutationResult.ReadOnlySynced, repository.delete(id))

        assertEquals(before, db.snapshot())
    }

    @Test
    fun inFlightEventsCannotBeEditedOrDeleted() = runTest {
        val id = record()
        val op = db.pendingOpDao().getByEntityId("FIELD_EVENT", id.toString())!!
        db.pendingOpDao().setState(listOf(op.seq), OpState.IN_FLIGHT)
        assertTrue(repository.observeEvent(id).first()!!.inFlight)
        val before = db.snapshot()

        assertEquals(MutationResult.InFlight, repository.updateQuantity(id, "9"))
        assertEquals(MutationResult.InFlight, repository.delete(id))

        assertEquals(before, db.snapshot())
    }
}
