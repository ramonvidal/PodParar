package com.podparar.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.podparar.core.model.SkipLengths

/**
 * The session's player: the ExoPlayer as the notification, lock screen, headphones, Bluetooth
 * (steering-wheel buttons) and Android Auto see it. Main thread only.
 *
 * - Seek back / forward use the skip lengths from the settings (ExoPlayer's own increments are
 *   fixed at build time), so every skip matches the app's.
 * - Next / previous follow the "next and previous skip" setting ([nextPreviousSkip]):
 *   - on: next = [seekForward], previous = [seekBack], available whenever an episode is loaded;
 *   - off: next starts the first episode in Up next ([onNextEpisode]), available only while
 *     Up next isn't empty ([queueHasNext]); previous goes back to the start of the episode.
 *
 * The four next / previous commands are computed here, not by ExoPlayer, so the listeners this
 * player hands out are told whenever that set changes ([Player.Listener.onAvailableCommandsChanged]
 * with this player's commands, never the ExoPlayer's).
 */
@OptIn(UnstableApi::class)
internal class SessionPlayer(
    player: Player,
    /** Starts the first episode in Up next. Called on the main thread. */
    private val onNextEpisode: () -> Unit,
) : ForwardingPlayer(player) {

    var backMs: Long = SkipLengths.DEFAULT_BACK_MS
    var forwardMs: Long = SkipLengths.DEFAULT_FORWARD_MS

    /** Next / previous skip forward / back instead of changing episode. */
    var nextPreviousSkip: Boolean = true
        set(value) {
            field = value
            refreshCommands()
        }

    /** Whether Up next has an episode to start. */
    var queueHasNext: Boolean = false
        set(value) {
            field = value
            refreshCommands()
        }

    /** Each listener given to [addListener], and the wrapper actually registered for it. */
    private val listeners = LinkedHashMap<Player.Listener, CommandsListener>()

    /** The commands the listeners were last told about; null before the first time. */
    private var lastSent: Player.Commands? = null

    init {
        // After every batch of player events, the loaded item (and so the commands) may have changed.
        super.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refreshCommands()
            }
        })
    }

    override fun addListener(listener: Player.Listener) {
        if (listeners.containsKey(listener)) return
        val wrapper = CommandsListener(listener)
        listeners[listener] = wrapper
        super.addListener(wrapper)
    }

    override fun removeListener(listener: Player.Listener) {
        val wrapper = listeners.remove(listener) ?: return
        super.removeListener(wrapper)
    }

    override fun getAvailableCommands(): Player.Commands {
        val loaded = currentMediaItem != null
        val builder = super.getAvailableCommands().buildUpon().removeAll(*NEXT_PREVIOUS)
        if (nextPreviousSkip) {
            if (loaded) builder.addAll(*NEXT_PREVIOUS)
        } else {
            if (loaded) builder.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (queueHasNext) builder.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean =
        if (nextPreviousSkip) currentMediaItem != null else queueHasNext

    override fun hasPreviousMediaItem(): Boolean =
        nextPreviousSkip && currentMediaItem != null

    override fun seekToNext() = skipToNext()

    override fun seekToNextMediaItem() = skipToNext()

    override fun seekToPrevious() = skipToPrevious()

    override fun seekToPreviousMediaItem() = skipToPrevious()

    // The deprecated names of the same calls, so nothing reaches the wrapped player's own.
    @Deprecated("Deprecated in Java", ReplaceWith("hasNextMediaItem()"))
    override fun hasNext(): Boolean = hasNextMediaItem()

    @Deprecated("Deprecated in Java", ReplaceWith("hasNextMediaItem()"))
    override fun hasNextWindow(): Boolean = hasNextMediaItem()

    @Deprecated("Deprecated in Java", ReplaceWith("seekToNextMediaItem()"))
    override fun next() = skipToNext()

    @Deprecated("Deprecated in Java", ReplaceWith("seekToNextMediaItem()"))
    override fun seekToNextWindow() = skipToNext()

    @Deprecated("Deprecated in Java", ReplaceWith("seekToPreviousMediaItem()"))
    override fun seekToPreviousWindow() = skipToPrevious()

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

    private fun skipToNext() {
        if (nextPreviousSkip) {
            seekForward()
        } else if (queueHasNext) {
            onNextEpisode()
        }
    }

    private fun skipToPrevious() {
        if (currentMediaItem == null) return
        if (nextPreviousSkip) seekBack() else seekTo(0L)
    }

    /** Tells the listeners when the available commands differ from what they last heard. */
    private fun refreshCommands() {
        val now = availableCommands
        if (now == lastSent) return
        lastSent = now
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_AVAILABLE_COMMANDS_CHANGED).build())
        // A copy: a listener may add or remove listeners while it's told.
        for (wrapper in listeners.values.toList()) {
            wrapper.listener.onAvailableCommandsChanged(now)
            wrapper.listener.onEvents(this, events)
        }
    }

    /**
     * Passes everything through ([ForwardingPlayer] already swaps in this player for onEvents),
     * except that available commands are this player's, not the wrapped one's.
     */
    private inner class CommandsListener(listener: Player.Listener) : ListenerForwarder(listener) {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            val now = this@SessionPlayer.availableCommands
            lastSent = now
            listener.onAvailableCommandsChanged(now)
        }
    }

    private companion object {
        val NEXT_PREVIOUS = intArrayOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
    }
}
