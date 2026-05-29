package com.buildsession.betterYAMF.manager.di

import com.buildsession.betterYAMF.manager.core.domain.repository.IReyamfRepository
import com.buildsession.betterYAMF.manager.core.repository.ReyamfRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
abstract class ReyamfRepositoryModule {
    @Binds
    abstract fun provideAppRepository(
        reyamfRepository: ReyamfRepository
    ): IReyamfRepository
}
