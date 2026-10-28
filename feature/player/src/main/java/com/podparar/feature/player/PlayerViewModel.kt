package com.podparar.feature.player

import androidx.lifecycle.ViewModel
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.SleepTimer
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

    /** The running sleep timer, or null when none is set. */
    val sleepTimer: StateFlow<SleepTimer?> = controller.sleepTimer

    /** The skip lengths in use, for the skip buttons' icons and labels. */
    val skipBackMs: StateFlow<Long> = controller.skipBackMs
    val skipForwardMs: StateFlow<Long> = controller.skipForwardMs

    fun togglePlayPause() = controller.togglePlayPause()

    fun skipBack() = controller.skipBack()

    fun skipForward() = controller.skipForward()

    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)

    fun setSpeed(speed: Float) = controller.setSpeed(speed)

    fun setSleepTimer(minutes: Int) = controller.setSleepTimer(minutes)

    fun setSleepAtEndOfEpisode() = controller.setSleepAtEndOfEpisode()

    fun extendSleepTimer() = controller.extendSleepTimer()

    fun cancelSleepTimer() = controller.cancelSleepTimer()
}
