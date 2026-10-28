package com.podparar.core.downloads

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DownloadsModule {
    @Binds
    @Singleton
    abstract fun bindEpisodeDownloads(impl: WorkManagerEpisodeDownloads): EpisodeDownloads
}
