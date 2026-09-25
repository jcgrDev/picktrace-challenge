package com.jcgrdev.picktracechallenge.di

import com.jcgrdev.picktracechallenge.BuildConfig
import com.jcgrdev.picktracechallenge.core.network.di.SyncBaseUrl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object NetworkConfigModule {

    /** From the `picktrace.syncBaseUrl` Gradle property (plan.md Open Question 4). */
    @Provides
    @SyncBaseUrl
    fun provideSyncBaseUrl(): String = BuildConfig.SYNC_BASE_URL
}
