package com.podparar

import com.podparar.core.update.UpdateChannel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Google Play updates the Play build; it has no self-updater (and no REQUEST_INSTALL_PACKAGES).
@Module
@InstallIn(SingletonComponent::class)
object DistributionModule {
    @Provides
    fun updateChannel(): UpdateChannel = UpdateChannel(selfUpdate = false)
}
