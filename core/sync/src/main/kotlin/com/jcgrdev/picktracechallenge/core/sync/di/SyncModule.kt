package com.jcgrdev.picktracechallenge.core.sync.di

import com.jcgrdev.picktracechallenge.core.model.IdGenerator
import com.jcgrdev.picktracechallenge.core.model.Uuid7
import com.jcgrdev.picktracechallenge.core.sync.SyncEngine
import com.jcgrdev.picktracechallenge.core.sync.SyncRunner
import com.jcgrdev.picktracechallenge.core.sync.SyncScheduler
import com.jcgrdev.picktracechallenge.core.sync.trigger.ForegroundConnectivityTrigger
import com.jcgrdev.picktracechallenge.core.sync.trigger.SyncTrigger
import com.jcgrdev.picktracechallenge.core.sync.work.WorkManagerSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SyncModule {

    @Binds
    abstract fun bindSyncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds
    abstract fun bindSyncRunner(impl: SyncEngine): SyncRunner

    @Binds
    abstract fun bindSyncTrigger(impl: ForegroundConnectivityTrigger): SyncTrigger

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @Singleton
        fun provideIdGenerator(clock: Clock): IdGenerator = Uuid7(clock)
    }
}
