package com.jcgrdev.picktracechallenge.core.data.di

import com.jcgrdev.picktracechallenge.core.data.FieldEventRepository
import com.jcgrdev.picktracechallenge.core.data.OfflineFirstFieldEventRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindFieldEventRepository(impl: OfflineFirstFieldEventRepository): FieldEventRepository
}
