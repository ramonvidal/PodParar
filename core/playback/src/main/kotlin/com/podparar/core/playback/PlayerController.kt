package com.podparar.core.playback

import com.podparar.core.model.NowPlaying
import com.podparar.core.model.SleepTimer
import kotlinx.coroutines.flow.StateFlow

/**
 * The player as the UI sees it. Media3 types never leave `:core:playback`; this speaks
 * `:core:model` only. Commands may be issued before the player is connected: they are
 * queued and run, in order, once it is.
 */
interface PlayerController {
    /** Null when nothing is loaded. While playing, positionMs updates at least every 500 ms. */
    val nowPlaying: StateFlow<NowPlaying?>

    /** The running sleep timer, null when none. */
    val sleepTimer: StateFlow<SleepTimer?>

    /** The skip lengths in use (from the settings), for the UI's icons and labels. */
    val skipBackMs: StateFlow<Long>
    val skipForwardMs: StateFlow<Long>

    /**
     * Loads [episodeId] and plays from PlaybackRules.resumePositionMs(episode) at the show's
     * speed (Podcast.speed), else the last speed used; if it's already the loaded episode,
     * toggles play/pause instead. Unknown id: nothing.
     */
    fun play(episodeId: String)

    fun togglePlayPause()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Back PlaybackSettings.skipBackMs, not before the start. */
    fun skipBack()

    /** Forward PlaybackSettings.skipForwardMs, not past the end when it's known. */
    fun skipForward()

    /**
     * Applies PlaybackSpeeds.clamp(speed) now and remembers it as the loaded show's speed
     * and as the last speed used.
     */
    fun setSpeed(speed: Float)

    /** Pauses after [minutes] (fading out over the last seconds). Replaces any running timer. */
    fun setSleepTimer(minutes: Int)

    /** Pauses when the playing episode ends (Up next doesn't carry on). Replaces any running timer. */
    fun setSleepAtEndOfEpisode()

    /** Adds SleepTimers.EXTEND_MINUTES to a timed timer. */
    fun extendSleepTimer()

    /** Turns the timer off; the volume goes back to full. */
    fun cancelSleepTimer()
}
