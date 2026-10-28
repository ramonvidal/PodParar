package com.podparar.core.playback

import com.podparar.core.model.NowPlaying
import kotlinx.coroutines.flow.StateFlow

/**
 * The player as the UI sees it. Media3 types never leave `:core:playback`; this speaks
 * `:core:model` only. Commands may be issued before the player is connected: they are
 * queued and run, in order, once it is.
 */
interface PlayerController {
    /** Null when nothing is loaded. While playing, positionMs updates at least every 500 ms. */
    val nowPlaying: StateFlow<NowPlaying?>

    /**
     * Loads [episodeId] and plays from PlaybackRules.resumePositionMs(episode) at the saved speed;
     * if it's already the loaded episode, toggles play/pause instead. Unknown id: nothing.
     */
    fun play(episodeId: String)

    fun togglePlayPause()

    fun pause()

    fun seekTo(positionMs: Long)

    /** Back PlaybackRules.SKIP_BACK_MS, not before the start. */
    fun skipBack()

    /** Forward PlaybackRules.SKIP_FORWARD_MS, not past the end when it's known. */
    fun skipForward()

    /** Applies PlaybackSpeeds.clamp(speed) now and remembers it in PlaybackSettings. */
    fun setSpeed(speed: Float)
}
