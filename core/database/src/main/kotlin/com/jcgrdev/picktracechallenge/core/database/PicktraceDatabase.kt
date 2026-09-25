package com.jcgrdev.picktracechallenge.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jcgrdev.picktracechallenge.core.database.dao.FieldEventDao
import com.jcgrdev.picktracechallenge.core.database.dao.PendingOpDao
import com.jcgrdev.picktracechallenge.core.database.dao.SyncRunDao
import com.jcgrdev.picktracechallenge.core.database.dao.SyncStateDao
import com.jcgrdev.picktracechallenge.core.database.entity.FieldEventEntity
import com.jcgrdev.picktracechallenge.core.database.entity.PendingOpEntity
import com.jcgrdev.picktracechallenge.core.database.entity.SyncRunEntity
import com.jcgrdev.picktracechallenge.core.database.entity.SyncStateEntity
import java.security.SecureRandom

@Database(
    entities = [FieldEventEntity::class, PendingOpEntity::class, SyncStateEntity::class, SyncRunEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PicktraceDatabase : RoomDatabase() {

    abstract fun fieldEventDao(): FieldEventDao
    abstract fun pendingOpDao(): PendingOpDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun syncRunDao(): SyncRunDao

    companion object {
        const val FILE_NAME = "picktrace.db"

        /** Applied by production and test builders alike, so every database is seeded the same way. */
        fun <T : RoomDatabase.Builder<PicktraceDatabase>> T.picktraceDefaults(): T = apply {
            addCallback(SeedSyncState)
        }
    }

    /** One random HLC node id per install (CLAUDE.md decision 10), created with the database. */
    private object SeedSyncState : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val nodeId = "%016x".format(SecureRandom().nextLong())
            db.execSQL(
                "INSERT INTO sync_state (id, node_id, hlc_wall_ms, hlc_counter) VALUES (?, ?, 0, 0)",
                arrayOf<Any>(SyncStateEntity.SINGLETON_ID, nodeId),
            )
        }
    }
}
