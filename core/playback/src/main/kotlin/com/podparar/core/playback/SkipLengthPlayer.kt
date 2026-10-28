package com.podparar.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.podparar.core.model.SkipLengths

/**
 * The session's player: the ExoPlayer with seek back / forward using the skip lengths from
 * the settings (ExoPlayer's own increments are fixed at build time), so notification,
 * lock screen and headphone skips match the app's. Main thread only.
 */
@OptIn(UnstableApi::class)
internal class SkipLengthPlayer(player: Player) : ForwardingPlayer(player) {
    var backMs: Long = SkipLengths.DEFAULT_BACK_MS
    var forwardMs: Long = SkipLengths.DEFAULT_FORWARD_MS

    override fun getSeekBackIncrement(): Long = backMs

    override fun getSeekForwardIncrement(): Long = forwardMs

    override fun seekBack() {
        if (currentMediaItem == null) return
        seekTo((currentPosition - backMs).coerceAtLeast(0L))
    }

    override fun seekForward() {
        if (currentMediaItem == null) return
        val target = currentPosition + forwardMs
        val duration = duration
        seekTo(if (duration != C.TIME_UNSET && duration > 0) target.coerceAtMost(duration) else target)
    }
}
