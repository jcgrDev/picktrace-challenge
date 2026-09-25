package com.jcgrdev.picktracechallenge.core.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.model.SyncStatus
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FieldEventDaoTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val dao = db.fieldEventDao()

    @After
    fun close() = db.close()

    private fun event(n: Int, status: SyncStatus = SyncStatus.PENDING) = FieldEventEntity(
        id = "00000000-0000-0000-0000-00000000000$n",
        workerId = "w_00$n",
        blockId = "block_$n",
        quantity = n,
        timestampMs = 1_749_544_320_000L + n,
        status = status,
    )

    @Test
    fun insertAndReadBack() = runTest {
        dao.insert(event(1))
        assertEquals(event(1), dao.getById(event(1).id))
    }

    @Test
    fun observeAllIsNewestFirstByRecordingOrder() = runTest {
        listOf(event(2), event(1), event(3)).forEach { dao.insert(it) }
        assertEquals(listOf(event(3), event(2), event(1)), dao.observeEvents(null).first())
    }

    @Test
    fun observeWithStatusFilters() = runTest {
        dao.insert(event(1, SyncStatus.PENDING))
        dao.insert(event(2, SyncStatus.SYNCED))
        dao.insert(event(3, SyncStatus.FAILED))
        dao.insert(event(4, SyncStatus.PENDING))
        assertEquals(listOf(event(4), event(1)), dao.observeEvents(SyncStatus.PENDING).first())
        assertEquals(listOf(event(2, SyncStatus.SYNCED)), dao.observeEvents(SyncStatus.SYNCED).first())
        assertEquals(listOf(event(3, SyncStatus.FAILED)), dao.observeEvents(SyncStatus.FAILED).first())
    }

    @Test
    fun observeEmitsOnInsert() = runTest {
        dao.observeEvents(null).test {
            assertEquals(emptyList<FieldEventEntity>(), awaitItem())
            dao.insert(event(1))
            assertEquals(listOf(event(1)), awaitItem())
            dao.insert(event(2))
            assertEquals(listOf(event(2), event(1)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
