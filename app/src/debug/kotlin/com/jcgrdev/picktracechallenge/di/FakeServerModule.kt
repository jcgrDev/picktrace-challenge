package com.jcgrdev.picktracechallenge.di

import com.jcgrdev.picktracechallenge.core.network.di.SyncInterceptors
import com.jcgrdev.picktracechallenge.core.testing.FakeSyncServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import javax.inject.Singleton

/**
 * Debug builds talk to the in-process FakeSyncServer until the backend exists (Principle V).
 * Release builds contribute nothing to the interceptor set, which NetworkModule declares with @Multibinds.
 */
@Module
@InstallIn(SingletonComponent::class)
object FakeServerModule {

    @Provides
    @Singleton
    fun provideFakeSyncServer(json: Json): FakeSyncServer = FakeSyncServer(json).apply {
        // Fake-only rule so US5 can be exercised by hand (contracts/fake-sync-server.md).
        rejectWhen("Rejected by FakeSyncServer: quantity >= 1000") { op ->
            (op.fields?.get("quantity")?.jsonPrimitive?.int ?: 0) >= 1000
        }
    }

    @Provides
    @IntoSet
    @SyncInterceptors
    fun provideFakeSyncServerInterceptor(server: FakeSyncServer): Interceptor = server
}
