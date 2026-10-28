package com.podparar.core.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.model.Episode
import com.podparar.core.model.PlaybackRules
import com.podparar.core.model.Podcast
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Hosts the ExoPlayer and the MediaSession that the notification, lock screen, headphones
 * and [MediaControllerPlayerController] talk to. Keeps the database's position and played
 * state up to date while it plays. When an episode ends, it carries on with the first
 * episode in Up next ([QueueRepository.takeNext]).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
internal class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var episodes: EpisodeRepository

    @Inject
    lateinit var queue: QueueRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Keeps database writes in the order they were issued. */
    private val writeLock = Mutex()

    private var session: MediaSession? = null
    private var saveTicker: Job? = null

    /** Last known position of the current item, so it can be saved after the item changes. */
    private var lastKnown: Snapshot? = null

    private data class Snapshot(val episodeId: String, val positionMs: Long, val durationMs: Long)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(PlaybackRules.SKIP_BACK_MS)
            .setSeekForwardIncrementMs(PlaybackRules.SKIP_FORWARD_MS)
            .build()
        player.addListener(PositionSaver(player))

        val builder = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setMediaButtonPreferences(mediaButtons())
        sessionActivity()?.let { builder.setSessionActivity(it) }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        val playing = player != null &&
            player.playWhenReady &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE
        if (!playing) stopSelf()
    }

    override fun onDestroy() {
        saveTicker?.cancel()
        saveTicker = null
        val current = session?.player
        // Captured on the main thread; the write outlives the scope's cancellation below.
        val last = current
            ?.takeIf { it.playbackState != Player.STATE_ENDED }
            ?.let(::snapshotOf)
        if (last != null) {
            scope.launch { withContext(NonCancellable) { savePosition(last) } }
        }
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    // Back 10 / play-pause (added by Media3) / forward 30 on the notification and lock screen.
    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName(getString(R.string.playback_skip_back))
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setDisplayName(getString(R.string.playback_skip_forward))
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private fun sessionActivity(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun snapshotOf(player: Player): Snapshot? {
        val item = player.currentMediaItem ?: return null
        if (item.mediaId.isEmpty()) return null
        return Snapshot(
            episodeId = item.mediaId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = EpisodeMediaItems.durationOf(player),
        )
    }

    /** Saves the player's current position now (values read here, on the main thread). */
    private fun saveNow(player: Player) {
        // Once it has ended, the ended handler owns the item's state.
        if (player.playbackState == Player.STATE_ENDED) return
        val snapshot = snapshotOf(player) ?: return
        lastKnown = snapshot
        write { savePosition(snapshot) }
    }

    private suspend fun savePosition(snapshot: Snapshot) {
        withContext(Dispatchers.IO) {
            episodes.savePosition(snapshot.episodeId, snapshot.positionMs, snapshot.durationMs)
        }
    }

    private fun write(block: suspend () -> Unit) {
        scope.launch {
            writeLock.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't save playback state", e)
                }
            }
        }
    }

    private inner class PositionSaver(private val player: Player) : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            saveTicker?.cancel()
            saveTicker = null
            if (isPlaying) {
                saveTicker = scope.launch {
                    while (isActive) {
                        delay(PlaybackRules.SAVE_INTERVAL_MS)
                        saveNow(player)
                    }
                }
            } else {
                saveNow(player)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // The exact position the old item was left at, when the player reports it.
            val known = lastKnown ?: return
            val oldId = oldPosition.mediaItem?.mediaId ?: return
            if (oldId == known.episodeId && oldId != newPosition.mediaItem?.mediaId) {
                lastKnown = known.copy(positionMs = oldPosition.positionMs.coerceAtLeast(0L))
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val previous = lastKnown
            if (previous != null && previous.episodeId != mediaItem?.mediaId) {
                write { savePosition(previous) }
            }
            lastKnown = snapshotOf(player)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            val item = player.currentMediaItem ?: return
            val id = item.mediaId
            if (id.isEmpty()) return
            val durationMs = EpisodeMediaItems.durationOf(player)
            lastKnown = null
            write {
                withContext(Dispatchers.IO) {
                    if (durationMs > 0) episodes.savePosition(id, durationMs, durationMs)
                    episodes.setPlayed(id, true)
                }
            }
            // A separate write, so a failed save doesn't stop the queue from carrying on.
            write { continueWithQueue(player, endedId = id) }
        }
    }

    /**
     * After [endedId] finished: loads the next queued episode on the same player, at its
     * resume position and the current speed, and plays it. Empty queue: stays stopped.
     * Runs inside [write], after the ended episode's writes; player calls stay on the
     * main thread.
     */
    private suspend fun continueWithQueue(player: Player, endedId: String) {
        val next = withContext(Dispatchers.IO) { takeNextPlayable() } ?: return
        val (episode, podcast) = next
        // Something else may have been started (or the ended one replayed) meanwhile:
        // then leave the player alone and put the episode back at the front of Up next.
        val stillEnded = session != null &&
            player.playbackState == Player.STATE_ENDED &&
            player.currentMediaItem?.mediaId == endedId
        if (!stillEnded) {
            if (player.currentMediaItem?.mediaId != episode.id) {
                withContext(Dispatchers.IO) { queue.playNext(episode.id) }
            }
            return
        }
        val speed = player.playbackParameters.speed
        player.setMediaItem(
            EpisodeMediaItems.build(episode, podcast),
            PlaybackRules.resumePositionMs(episode),
        )
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
    }

    /** Takes queued ids until one still names an episode; null when the queue runs out. */
    private suspend fun takeNextPlayable(): Pair<Episode, Podcast?>? {
        while (true) {
            val id = queue.takeNext() ?: return null
            val episode = episodes.getEpisode(id) ?: continue
            return episode to episodes.getPodcast(episode.podcastUrl)
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(EpisodeMediaItems::resolve).toMutableList())
    }

    private companion object {
        const val TAG = "PlaybackService"
    }
}
