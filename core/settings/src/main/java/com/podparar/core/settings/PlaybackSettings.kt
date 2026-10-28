package com.podparar.core.settings

import kotlinx.coroutines.flow.Flow

/** This phone's playback choices. */
interface PlaybackSettings {
    /** Last speed chosen; PlaybackSpeeds.DEFAULT until set. */
    val speed: Flow<Float>

    /** Skip silent parts of episodes; false until set. */
    val skipSilence: Flow<Boolean>

    /** How far skip back jumps; SkipLengths.DEFAULT_BACK_MS until set. */
    val skipBackMs: Flow<Long>

    /** How far skip forward jumps; SkipLengths.DEFAULT_FORWARD_MS until set. */
    val skipForwardMs: Flow<Long>

    /** Shake the phone to add time to a running sleep timer; true until set. */
    val shakeToExtend: Flow<Boolean>

    /** Stores PlaybackSpeeds.clamp(speed). */
    suspend fun setSpeed(speed: Float)

    suspend fun setSkipSilence(enabled: Boolean)

    /** Stores SkipLengths.nearestBack(ms). */
    suspend fun setSkipBackMs(ms: Long)

    /** Stores SkipLengths.nearestForward(ms). */
    suspend fun setSkipForwardMs(ms: Long)

    suspend fun setShakeToExtend(enabled: Boolean)
}
