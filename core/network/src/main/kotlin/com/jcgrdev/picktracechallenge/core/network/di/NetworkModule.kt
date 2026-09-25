package com.jcgrdev.picktracechallenge.core.network.di

import com.jcgrdev.picktracechallenge.core.network.SyncApi
import com.jcgrdev.picktracechallenge.core.network.SyncJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton

/** Base URL of the sync backend. Bound by `:app` from `BuildConfig`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncBaseUrl

/** Application interceptors for the sync client. Debug `:app` contributes FakeSyncServer. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SyncInterceptors

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** Declared so the set may be empty (release builds contribute nothing). */
    @Multibinds
    @SyncInterceptors
    abstract fun syncInterceptors(): Set<Interceptor>

    companion object {
        @Provides
        @Singleton
        fun provideJson(): Json = SyncJson

        @Provides
        @Singleton
        fun provideOkHttpClient(
            @SyncInterceptors interceptors: Set<@JvmSuppressWildcards Interceptor>,
        ): OkHttpClient = OkHttpClient.Builder()
            .apply { interceptors.forEach(::addInterceptor) }
            .build()

        @Provides
        @Singleton
        fun provideRetrofit(
            @SyncBaseUrl baseUrl: String,
            client: OkHttpClient,
            json: Json,
        ): Retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        @Provides
        @Singleton
        fun provideSyncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)
    }
}
