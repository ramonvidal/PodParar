package com.podparar.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindPodcastRepository(impl: DefaultPodcastRepository): PodcastRepository

    @Binds
    @Singleton
    abstract fun bindEpisodeRepository(impl: DefaultEpisodeRepository): EpisodeRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object ClockModule {
    @Provides
    fun clock(): Clock = Clock.systemUTC()
}
