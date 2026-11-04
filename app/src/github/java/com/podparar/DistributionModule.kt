package com.podparar

import com.podparar.core.update.UpdateChannel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// The GitHub build updates itself from the release page.
@Module
@InstallIn(SingletonComponent::class)
object DistributionModule {
    @Provides
    fun updateChannel(): UpdateChannel = UpdateChannel(selfUpdate = true)
}
