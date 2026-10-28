package com.podparar.core.playback

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlayerController(impl: MediaControllerPlayerController): PlayerController
}
