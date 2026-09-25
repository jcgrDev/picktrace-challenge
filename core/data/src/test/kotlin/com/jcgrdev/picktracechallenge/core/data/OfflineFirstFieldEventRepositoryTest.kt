package com.jcgrdev.picktracechallenge.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.model.BlockId
import com.jcgrdev.picktracechallenge.core.model.FieldEvent
import com.jcgrdev.picktracechallenge.core.model.Hlc
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.model.ValidationError
import com.jcgrdev.picktracechallenge.core.model.WorkerId
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import com.jcgrdev.picktracechallenge.core.sync.FieldEventCodec
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncScheduler
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import com.jcgrdev.picktracechallenge.core.testing.fixtures.sampleDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class OfflineFirstFieldEventRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = TestDatabases.inMemory(context)
    private val scheduler = FakeSyncScheduler()
    private val repository = repositoryFor(db, scheduler)

    @After
    fun close() = db.close()

    @Test
    fun recordStoresPendingEventAndQueuedCreateOp() = runTest {
        val draft = sampleDraft(workerId = "  w_001 ", blockId = " block_42", timestamp = Instant.parse("2025-06-10T08:32:00.123456789Z"))

        val result = repository.record(draft)

        val id = (result as RecordResult.Recorded).id
        assertEquals(7, id.version())
        val expected = FieldEvent(id, WorkerId("w_001"), BlockId("block_42"), 3, Instant.parse("2025-06-10T08:32:00.123Z"), SyncStatus.PENDING)
        assertEquals(listOf(expected), repository.observeEvents().first())

        val op = db.pendingOpDao().getByEntityId("FIELD_EVENT", id.toString())
        assertNotNull(op)
        op!!
        assertEquals(OpState.QUEUED, op.state)
        assertEquals(0, op.attempts)
        assertEquals("CREATE", op.opType)
        assertEquals(1, op.schemaVersion)
        assertEquals(FieldEventCodec(SyncJson).encodeToString(expected), op.fieldsJson)
        assertEquals(7, java.util.UUID.fromString(op.opId).version())
        assertTrue(op.opId != id.toString())
        Hlc.parse(op.hlc) // throws if malformed
        assertEquals(listOf(FakeSyncScheduler.Request(expedited = false)), scheduler.requests)
    }

    @Test
    fun invalidDraftReportsAllErrorsAndWritesNothing() = runTest {
        val result = repository.record(sampleDraft(workerId = "", blockId = " ", quantity = "0"))

        assertEquals(
            RecordResult.Invalid(listOf(ValidationError.WorkerIdMissing, ValidationError.BlockIdMissing, ValidationError.QuantityNotPositive)),
            result,
        )
        assertEquals(emptyList<FieldEvent>(), repository.observeEvents().first())
        assertEquals(0, db.pendingOpDao().count())
        assertEquals(emptyList<FakeSyncScheduler.Request>(), scheduler.requests)
    }

    @Test
    fun storageFullIsReportedAndLeavesNoPartialRows() = runTest {
        val fileDb = TestDatabases.fileBacked(context, "full-${System.nanoTime()}.db")
        val fileScheduler = FakeSyncScheduler()
        val fileRepository = repositoryFor(fileDb, fileScheduler)
        fileRepository.record(sampleDraft()) // creates tables and first pages
        // PRAGMA max_page_count is per connection; inside a transaction it lands on the write connection.
        val sqlite = fileDb.openHelper.writableDatabase
        sqlite.beginTransaction()
        try {
            val pages = sqlite.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }
            sqlite.query("PRAGMA max_page_count = $pages").use { it.moveToFirst() }
            sqlite.setTransactionSuccessful()
        } finally {
            sqlite.endTransaction()
        }

        var recorded = 1
        var result: RecordResult
        do {
            result = fileRepository.record(sampleDraft(quantity = "${recorded + 1}"))
            if (result is RecordResult.Recorded) recorded++
        } while (result is RecordResult.Recorded && recorded < 10_000)

        assertEquals(RecordResult.StorageFull, result)
        assertEquals(recorded, fileRepository.observeEvents().first().size)
        assertEquals(recorded, fileDb.pendingOpDao().count())
        assertEquals(recorded, fileScheduler.requests.size)
        fileDb.close()
    }
}
