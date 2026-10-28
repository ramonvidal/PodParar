package com.podparar.core.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.PlaybackRules
import com.podparar.core.model.PlaybackSpeeds
import com.podparar.core.settings.PlaybackSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [PlayerController] backed by a Media3 [MediaController] connected to [PlaybackService].
 *
 * Every command goes through one queue, consumed on the main thread in order: a command
 * issued before the controller is connected waits for the connection, and a command issued
 * right after [play] runs once the new episode is loaded.
 */
@OptIn(UnstableApi::class)
internal class MediaControllerPlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodes: EpisodeRepository,
    private val settings: PlaybackSettings,
    private val queue: QueueRepository,
) : PlayerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val commands = Channel<suspend (MediaController) -> Unit>(Channel.UNLIMITED)

    private val state = MutableStateFlow<NowPlaying?>(null)
    override val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()

    // Touched on the main thread only.
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            if (this@MediaControllerPlayerController.controller === controller) {
                this@MediaControllerPlayerController.controller = null
                stopTicker()
                state.value = null
            }
            controller.release()
        }
    }

    init {
        scope.launch {
            // Connect straight away so nowPlaying reflects a session that's already playing.
            connectedController()
            for (command in commands) {
                val c = connectedController() ?: continue
                try {
                    command(c)
                } catch (e: CancellationException) {
                    // Our own cancellation propagates; a stray one (a cancelled future) must
                    // not end the queue.
                    if (!isActive) throw e
                    Log.w(TAG, "Player command cancelled", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Player command failed", e)
                }
                refresh()
            }
        }
    }

    override fun play(episodeId: String) = enqueue { c ->
        if (c.currentMediaItem?.mediaId == episodeId) {
            toggle(c)
            return@enqueue
        }
        val episode = episodes.getEpisode(episodeId) ?: return@enqueue
        val podcast = episodes.getPodcast(episode.podcastUrl)
        val speed = PlaybackSpeeds.clamp(settings.speed.first())
        // The controller may have dropped while loading: use whichever is connected now.
        val player = connectedController() ?: return@enqueue
        saveCurrentPosition(player)
        player.setMediaItem(
            EpisodeMediaItems.build(episode, podcast),
            PlaybackRules.resumePositionMs(episode),
        )
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
        removeFromQueue(episodeId)
    }

    override fun togglePlayPause() = enqueue(::toggle)

    override fun pause() = enqueue { c -> c.pause() }

    override fun seekTo(positionMs: Long) = enqueue { c ->
        if (c.currentMediaItem != null) c.seekTo(clampToEpisode(c, positionMs))
    }

    override fun skipBack() = enqueue { c ->
        if (c.currentMediaItem != null) {
            c.seekTo(clampToEpisode(c, c.currentPosition - PlaybackRules.SKIP_BACK_MS))
        }
    }

    override fun skipForward() = enqueue { c ->
        if (c.currentMediaItem != null) {
            c.seekTo(clampToEpisode(c, c.currentPosition + PlaybackRules.SKIP_FORWARD_MS))
        }
    }

    override fun setSpeed(speed: Float) {
        val clamped = PlaybackSpeeds.clamp(speed)
        enqueue { c -> c.setPlaybackSpeed(clamped) }
        scope.launch {
            try {
                settings.setSpeed(clamped)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remember the speed", e)
            }
        }
    }

    private fun enqueue(command: suspend (MediaController) -> Unit) {
        commands.trySend(command)
    }

    private fun toggle(c: MediaController) {
        if (c.currentMediaItem == null) return
        val playbackState = c.playbackState
        if (c.playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            c.pause()
            return
        }
        when (playbackState) {
            Player.STATE_IDLE -> c.prepare()
            Player.STATE_ENDED -> c.seekTo(0L)
        }
        c.play()
    }

    private fun clampToEpisode(c: MediaController, positionMs: Long): Long {
        val duration = EpisodeMediaItems.durationOf(c)
        val atLeastZero = positionMs.coerceAtLeast(0L)
        return if (duration > 0) atLeastZero.coerceAtMost(duration) else atLeastZero
    }

    /** Saves where the loaded episode is before another one replaces it. */
    private fun saveCurrentPosition(c: MediaController) {
        val id = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        // An ended episode is marked played by the service.
        if (c.playbackState == Player.STATE_ENDED) return
        val positionMs = c.currentPosition.coerceAtLeast(0L)
        val durationMs = EpisodeMediaItems.durationOf(c)
        scope.launch {
            try {
                withContext(Dispatchers.IO) { episodes.savePosition(id, positionMs, durationMs) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save the position", e)
            }
        }
    }

    /** The episode now playing isn't "up next" any more. */
    private fun removeFromQueue(episodeId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { queue.remove(episodeId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't update Up next", e)
            }
        }
    }

    /** The connected controller, connecting first when needed; null if connecting failed. */
    private suspend fun connectedController(): MediaController? {
        controller?.let { if (it.isConnected) return it }
        controller?.release()
        controller = null
        repeat(CONNECT_ATTEMPTS) { attempt ->
            try {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                val connected = MediaController.Builder(context, token)
                    .setListener(controllerListener)
                    .buildAsync()
                    .await()
                connected.addListener(playerListener)
                controller = connected
                refresh()
                return connected
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                Log.w(TAG, "Connecting to the player was cancelled (attempt ${attempt + 1})", e)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't connect to the player (attempt ${attempt + 1})", e)
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        return null
    }

    private fun refresh() {
        val c = controller
        state.value = c?.let(EpisodeMediaItems::nowPlaying)
        if (c != null && c.isPlaying) startTicker() else stopTicker()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val c = controller ?: break
                state.value = EpisodeMediaItems.nowPlaying(c)
                if (!c.isPlaying) break
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private companion object {
        const val TAG = "PlayerController"
        const val TICK_MS = 400L
        const val CONNECT_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 500L
    }
}
