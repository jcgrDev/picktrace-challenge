package com.jcgrdev.picktracechallenge.core.sync.di

import com.jcgrdev.picktracechallenge.core.sync.SyncConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Batch size (default 50, clamped to 500) and the transport attempt limit (default 5). Kept in its own
 * module so it can be replaced as a unit; tests pass a SyncConfig to SyncEngine directly.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncConfigModule {

    @Provides
    fun provideSyncConfig(): SyncConfig = SyncConfig()
}
