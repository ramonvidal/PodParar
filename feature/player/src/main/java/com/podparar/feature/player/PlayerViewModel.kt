package com.podparar.feature.player

import androidx.lifecycle.ViewModel
import com.podparar.core.model.NowPlaying
import com.podparar.core.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared by the mini player and the full player. The player itself lives in `:core:playback`;
 * this only passes the listener's taps on to it.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val controller: PlayerController,
) : ViewModel() {

    /** What's loaded, or null when nothing is. */
    val nowPlaying: StateFlow<NowPlaying?> = controller.nowPlaying

    fun togglePlayPause() = controller.togglePlayPause()

    fun skipBack() = controller.skipBack()

    fun skipForward() = controller.skipForward()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun setSpeed(speed: Float) = controller.setSpeed(speed)
}
