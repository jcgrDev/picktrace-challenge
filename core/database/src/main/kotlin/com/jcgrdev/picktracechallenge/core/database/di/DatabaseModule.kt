package com.jcgrdev.picktracechallenge.core.database.di

import android.content.Context
import androidx.room.Room
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase
import com.jcgrdev.picktracechallenge.core.database.PicktraceDatabase.Companion.picktraceDefaults
import com.jcgrdev.picktracechallenge.core.database.dao.FieldEventDao
import com.jcgrdev.picktracechallenge.core.database.dao.PendingOpDao
import com.jcgrdev.picktracechallenge.core.database.dao.SyncRunDao
import com.jcgrdev.picktracechallenge.core.database.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PicktraceDatabase =
        Room.databaseBuilder(context, PicktraceDatabase::class.java, PicktraceDatabase.FILE_NAME)
            .picktraceDefaults()
            .build()

    @Provides
    fun provideFieldEventDao(db: PicktraceDatabase): FieldEventDao = db.fieldEventDao()

    @Provides
    fun providePendingOpDao(db: PicktraceDatabase): PendingOpDao = db.pendingOpDao()

    @Provides
    fun provideSyncStateDao(db: PicktraceDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideSyncRunDao(db: PicktraceDatabase): SyncRunDao = db.syncRunDao()
}
