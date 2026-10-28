package com.podparar.core.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton

/** The notification / lock screen skip buttons matching the skip lengths in use. */
@OptIn(UnstableApi::class)
internal object SkipButtons {
    fun build(context: Context, backMs: Long, forwardMs: Long): List<CommandButton> = listOf(
        CommandButton.Builder(backIcon(backMs))
            .setDisplayName(context.getString(R.string.playback_skip_back, seconds(backMs)))
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(forwardIcon(forwardMs))
            .setDisplayName(context.getString(R.string.playback_skip_forward, seconds(forwardMs)))
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private fun seconds(ms: Long): Int = (ms / 1_000L).toInt()

    private fun backIcon(ms: Long): Int = when (ms) {
        5_000L -> CommandButton.ICON_SKIP_BACK_5
        10_000L -> CommandButton.ICON_SKIP_BACK_10
        15_000L -> CommandButton.ICON_SKIP_BACK_15
        30_000L -> CommandButton.ICON_SKIP_BACK_30
        else -> CommandButton.ICON_SKIP_BACK
    }

    private fun forwardIcon(ms: Long): Int = when (ms) {
        5_000L -> CommandButton.ICON_SKIP_FORWARD_5
        10_000L -> CommandButton.ICON_SKIP_FORWARD_10
        15_000L -> CommandButton.ICON_SKIP_FORWARD_15
        30_000L -> CommandButton.ICON_SKIP_FORWARD_30
        else -> CommandButton.ICON_SKIP_FORWARD
    }
}
