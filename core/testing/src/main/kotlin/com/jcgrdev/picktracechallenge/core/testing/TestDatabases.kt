package com.jcgrdev.picktracechallenge.core.testing

import android.content.Context
import androidx.room.Room
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase.Companion.picktraceDefaults

/**
 * Databases for Robolectric tests, seeded exactly like production. They keep Room's default
 * executors: routing Room onto a single test-dispatcher thread deadlocks `withTransaction`, which
 * parks a thread for the transaction.
 */
object TestDatabases {

    fun inMemory(context: Context): PicktraceDatabase =
        Room.inMemoryDatabaseBuilder(context, PicktraceDatabase::class.java)
            .picktraceDefaults()
            .build()

    /** File-backed, so a test can close it and open a new instance on the same file ("relaunch"). */
    fun fileBacked(context: Context, name: String): PicktraceDatabase =
        Room.databaseBuilder(context, PicktraceDatabase::class.java, name)
            .picktraceDefaults()
            .build()
}
