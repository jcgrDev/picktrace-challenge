package com.jcgrdev.picktracechallenge.core.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jcgrdev.picktracechallenge.core.database.entity.OpState
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.testing.TestDatabases
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingOpDaoTest {

    private val db = TestDatabases.inMemory(ApplicationProvider.getApplicationContext())
    private val dao = db.pendingOpDao()

    @After
    fun close() = db.close()

    private fun op(opId: String, entityId: String) = PendingOpEntity(
        opId = opId,
        entityType = "FIELD_EVENT",
        entityId = entityId,
        opType = "CREATE",
        schemaVersion = 1,
        hlc = "0000000000000000001-0000-0000000000000000",
        fieldsJson = "{}",
        state = OpState.QUEUED,
    )

    @Test
    fun insertAssignsIncreasingSeq() = runTest {
        val first = dao.insert(op("op-1", "e-1"))
        val second = dao.insert(op("op-2", "e-2"))
        val third = dao.insert(op("op-3", "e-3"))
        assertTrue(first < second && second < third)
        assertEquals(second, dao.getByEntityId("FIELD_EVENT", "e-2")!!.seq)
    }

    @Test
    fun duplicateOpIdViolatesUniqueIndex() = runTest {
        dao.insert(op("op-1", "e-1"))
        val failure = runCatching { dao.insert(op("op-1", "e-2")) }.exceptionOrNull()
        assertTrue("$failure", failure is SQLiteConstraintException)
    }

    @Test
    fun secondOpForSameEntityViolatesUniqueIndex() = runTest {
        dao.insert(op("op-1", "e-1"))
        val failure = runCatching { dao.insert(op("op-2", "e-1")) }.exceptionOrNull()
        assertTrue("$failure", failure is SQLiteConstraintException)
    }
}
