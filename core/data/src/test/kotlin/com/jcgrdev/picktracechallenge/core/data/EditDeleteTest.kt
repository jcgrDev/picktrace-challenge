package com.jcgrdev.picktracechallenge.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import com.jcgrdev.picktracechallenge.core.testing.Invariants
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.fixtures.sampleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** US4-3, US4-4, FR-007, FR-008, FR-009, FR-009b. */
@RunWith(AndroidJUnit4::class)
class EditDeleteTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val repository = repositoryFor(db)
    private val server = FakeSyncServer()

    @After
    fun close() = db.close()

    private suspend fun record(quantity: String = "3"): UUID =
        (repository.record(sampleDraft(quantity = quantity)) as RecordResult.Recorded).id

    private suspend fun op(id: UUID) = db.pendingOpDao().getByEntityId("FIELD_EVENT", id.toString())!!

    @Test
    fun editingAPendingEventRewritesItsCreateOpInPlace() = runTest {
        val id = record("3")
        val before = op(id)

        assertEquals(MutationResult.Success, repository.updateQuantity(id, "8"))

        val detail = repository.observeEvent(id).first()!!
        assertEquals(8, detail.event.quantity)
        assertEquals(SyncStatus.PENDING, detail.event.status)
        val after = op(id)
        assertEquals(before.opId, after.opId)
        assertEquals(before.seq, after.seq)
        assertTrue(after.fieldsJson, after.fieldsJson.contains("\"quantity\":8"))
        assertTrue(after.hlc > before.hlc)

        db.syncWith(server)
        assertEquals(8, server.deliveryLog.single().fields!!.getValue("quantity").jsonPrimitive.int)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun aFailedEventCanBeEditedAndStaysFailed() = runTest {
        val id = record("5000")
        server.rejectWhen("too many") { it.fields!!.getValue("quantity").jsonPrimitive.int > 1000 }
        db.syncWith(server)
        assertEquals(SyncStatus.FAILED, repository.observeEvent(id).first()!!.event.status)

        assertEquals(MutationResult.Success, repository.updateQuantity(id, "50"))

        val detail = repository.observeEvent(id).first()!!
        assertEquals(50, detail.event.quantity)
        assertEquals(SyncStatus.FAILED, detail.event.status)
        assertEquals("too many", detail.failureReason)
        assertEquals(emptyList<String>(), Invariants.violations(db))
    }

    @Test
    fun deletingAPendingEventRemovesBothRowsAndItIsNeverSent() = runTest {
        val kept = record("1")
        val deleted = record("2")

        assertEquals(MutationResult.Success, repository.delete(deleted))

        assertNull(repository.observeEvent(deleted).first())
        assertNull(db.pendingOpDao().getByEntityId("FIELD_EVENT", deleted.toString()))
        db.syncWith(server)
        assertEquals(listOf(kept.toString()), server.deliveryLog.map { it.entityId })
    }

    @Test
    fun anInvalidQuantityIsRejectedAndNothingChanges() = runTest {
        val id = record("3")
        val before = db.snapshot()
        assertEquals(MutationResult.Invalid(listOf(ValidationError.QuantityNotPositive)), repository.updateQuantity(id, "0"))
        assertEquals(MutationResult.Invalid(listOf(ValidationError.QuantityNotANumber)), repository.updateQuantity(id, "x"))
        assertEquals(before, db.snapshot())
    }

    @Test
    fun anUnknownIdIsNotFound() = runTest {
        assertEquals(MutationResult.NotFound, repository.updateQuantity(UUID(0, 99), "3"))
        assertEquals(MutationResult.NotFound, repository.delete(UUID(0, 99)))
    }
}
