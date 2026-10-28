package com.podparar.core.playback

import com.podparar.core.model.SleepTimer
import com.podparar.core.model.SleepTimers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The one running sleep timer, shared by [MediaControllerPlayerController] (which sets it
 * from the UI) and [PlaybackService] (which fades, pauses and handles end of episode).
 * Both live in the app's process, so a singleton is enough. Thread-safe.
 */
@Singleton
internal class SleepTimerController @Inject constructor() {
    private val state = MutableStateFlow<SleepTimer?>(null)
    val timer: StateFlow<SleepTimer?> = state.asStateFlow()

    fun setMinutes(minutes: Int, now: Long = System.currentTimeMillis()) {
        state.value = SleepTimers.after(minutes, now)
    }

    fun setEndOfEpisode() {
        state.value = SleepTimer(endsAt = null, endOfEpisode = true)
    }

    /** Adds SleepTimers.EXTEND_MINUTES to a timed timer; nothing otherwise. */
    fun extend() {
        state.update { current -> current?.let(SleepTimers::extend) }
    }

    fun cancel() {
        state.value = null
    }

    /** Clears [expected] only if it is still the running timer (not replaced meanwhile). */
    fun clearIf(expected: SleepTimer) {
        state.compareAndSet(expected, null)
    }
}
