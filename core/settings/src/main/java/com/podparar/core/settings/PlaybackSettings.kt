package com.podparar.core.settings

import kotlinx.coroutines.flow.Flow

/** This phone's playback choices. */
interface PlaybackSettings {
    /** Last speed chosen; PlaybackSpeeds.DEFAULT until set. */
    val speed: Flow<Float>

    /** Stores PlaybackSpeeds.clamp(speed). */
    suspend fun setSpeed(speed: Float)
}
