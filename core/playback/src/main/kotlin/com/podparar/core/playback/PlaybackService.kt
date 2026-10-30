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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.podparar.core.data.DownloadRepository
import com.podparar.core.data.EpisodeRepository
import com.podparar.core.data.PodcastRepository
import com.podparar.core.data.QueueRepository
import com.podparar.core.model.CarBrowse
import com.podparar.core.model.Episode
import com.podparar.core.model.PlaybackRules
import com.podparar.core.model.PlaybackSpeeds
import com.podparar.core.model.Podcast
import com.podparar.core.model.SkipLengths
import com.podparar.core.model.SleepTimers
import com.podparar.core.settings.PlaybackSettings
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Hosts the ExoPlayer and the media session that the notification, lock screen, headphones,
 * Bluetooth (car steering-wheel buttons), Android Auto and [MediaControllerPlayerController]
 * talk to. Keeps the database's position and played state up to date while it plays. When an
 * episode ends, it carries on with the first episode in Up next ([QueueRepository.takeNext]),
 * at that show's speed, unless the sleep timer was set to the end of the episode.
 *
 * Also applies this phone's playback settings (skip lengths, skip silence, next / previous
 * skipping, see [SessionPlayer]) and runs the [SleepTimerController]'s timer: the fade, the
 * pause, and shake to extend.
 *
 * It's a [MediaLibraryService] so Android Auto can browse it ([CarBrowse] tree: Continue
 * listening, Up next, New episodes, Your shows) and play from it. The car asks for an episode
 * by id only; [LibraryCallback] builds the full item from the database, like the app's play.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
internal class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var episodes: EpisodeRepository

    @Inject
    lateinit var queue: QueueRepository

    @Inject
    lateinit var podcasts: PodcastRepository

    @Inject
    lateinit var downloads: DownloadRepository

    @Inject
    lateinit var settings: PlaybackSettings

    @Inject
    lateinit var sleepTimers: SleepTimerController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Keeps database writes in the order they were issued. */
    private val writeLock = Mutex()

    private var session: MediaLibrarySession? = null
    private var saveTicker: Job? = null
    private var shakeDetector: ShakeDetector? = null

    /** Whether the player is playing, for the shake detector. Main thread. */
    private val isPlaying = MutableStateFlow(false)

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
            .build()
        player.addListener(PositionSaver(player))

        // The session sees the player through the skip lengths and next / previous setting.
        val forwarding = SessionPlayer(player, onNextEpisode = { startNextInQueue(player) })

        val builder = MediaLibrarySession.Builder(this, forwarding, LibraryCallback(player))
            .setMediaButtonPreferences(
                SkipButtons.build(this, SkipLengths.DEFAULT_BACK_MS, SkipLengths.DEFAULT_FORWARD_MS),
            )
        sessionActivity()?.let { builder.setSessionActivity(it) }
        session = builder.build()

        shakeDetector = ShakeDetector(this) { sleepTimers.extend() }
        observeSettings(player, forwarding)
        observeQueue(forwarding)
        observeCarLists()
        runSleepTimer(player)
        watchShakes()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

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
        shakeDetector?.stop()
        shakeDetector = null
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

    /** Skip lengths, next / previous skipping and skip silence follow the settings while the service lives. */
    private fun observeSettings(player: ExoPlayer, forwarding: SessionPlayer) {
        scope.launch {
            try {
                combine(settings.skipBackMs, settings.skipForwardMs) { back, forward -> back to forward }
                    .distinctUntilChanged()
                    .collect { (back, forward) ->
                        forwarding.backMs = back
                        forwarding.forwardMs = forward
                        session?.setMediaButtonPreferences(SkipButtons.build(this@PlaybackService, back, forward))
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read the skip lengths", e)
            }
        }
        scope.launch {
            try {
                settings.nextPreviousSkip.distinctUntilChanged().collect { enabled ->
                    forwarding.nextPreviousSkip = enabled
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read next / previous skipping", e)
            }
        }
        scope.launch {
            try {
                settings.skipSilence.distinctUntilChanged().collect { enabled ->
                    player.skipSilenceEnabled = enabled
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't read skip silence", e)
            }
        }
    }

    /** With next / previous changing episode, next is offered only while Up next has one. */
    private fun observeQueue(forwarding: SessionPlayer) {
        scope.launch {
            try {
                queue.queue.map { it.isNotEmpty() }.distinctUntilChanged().collect { hasNext ->
                    forwarding.queueHasNext = hasNext
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't watch Up next", e)
            }
        }
    }

    /**
     * Tells a car showing one of the lists that it changed (episodes added, removed or moved;
     * position saves alone don't count), so it reloads it.
     */
    private fun observeCarLists() {
        notifyOnChange(CarBrowse.CONTINUE, episodes.continueListening.map { list -> list.map { it.episode.id } })
        notifyOnChange(CarBrowse.UP_NEXT, queue.queue.map { list -> list.map { it.episode.id } })
        notifyOnChange(CarBrowse.NEW, episodes.newEpisodes.map { list -> list.map { it.episode.id } })
        notifyOnChange(CarBrowse.SHOWS, podcasts.podcasts.map { list -> list.map { it.feedUrl to it.title } })
    }

    private fun notifyOnChange(parentId: String, contents: Flow<List<Any>>) {
        scope.launch {
            try {
                contents.distinctUntilChanged().drop(1).collect { list ->
                    session?.notifyChildrenChanged(parentId, list.size.coerceAtMost(CarBrowse.MAX_ITEMS), null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't watch $parentId for the car", e)
            }
        }
    }

    /**
     * Timed sleep timer: the volume follows SleepTimers.volumeAt over the last seconds, and
     * at 0 the player pauses, the volume goes back to full and the timer clears. Replacing
     * or cancelling the timer restarts this with the volume back to full.
     */
    private fun runSleepTimer(player: ExoPlayer) {
        scope.launch {
            sleepTimers.timer.collectLatest { timer ->
                player.volume = 1f
                if (timer == null || timer.endsAt == null) return@collectLatest
                while (true) {
                    val now = System.currentTimeMillis()
                    val remaining = timer.remainingMs(now) ?: return@collectLatest
                    if (remaining <= 0L) {
                        player.pause()
                        player.volume = 1f
                        sleepTimers.clearIf(timer)
                        return@collectLatest
                    }
                    if (remaining > SleepTimers.FADE_MS) {
                        // Wait until the fade starts (rechecked, in case the clock moves).
                        delay((remaining - SleepTimers.FADE_MS).coerceAtMost(MAX_WAIT_MS))
                        continue
                    }
                    player.volume = SleepTimers.volumeAt(timer, now).coerceIn(0f, 1f)
                    delay(FADE_STEP_MS.coerceAtMost(remaining))
                }
            }
        }
    }

    /** Listens for shakes only while a timed timer runs, it's playing and shake to extend is on. */
    private fun watchShakes() {
        scope.launch {
            try {
                combine(sleepTimers.timer, isPlaying, settings.shakeToExtend) { timer, playing, shake ->
                    shake && playing && timer?.endsAt != null
                }
                    .distinctUntilChanged()
                    .collect { listen ->
                        if (listen) shakeDetector?.start() else shakeDetector?.stop()
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't watch for shakes", e)
                shakeDetector?.stop()
            }
        }
    }

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
            this@PlaybackService.isPlaying.value = isPlaying
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
            // Sleep timer set to the end of the episode: stop here, Up next waits.
            val timer = sleepTimers.timer.value
            if (timer != null && timer.endOfEpisode) {
                sleepTimers.clearIf(timer)
                return
            }
            // A separate write, so a failed save doesn't stop the queue from carrying on.
            write { continueWithQueue(player, endedId = id) }
        }
    }

    /**
     * After [endedId] finished: loads the next queued episode on the same player, at its
     * resume position and its show's speed (else the last speed used), and plays it.
     * Empty queue: stays stopped.
     * Runs inside [write], after the ended episode's writes; player calls stay on the
     * main thread.
     */
    private suspend fun continueWithQueue(player: Player, endedId: String) {
        val next = withContext(Dispatchers.IO) { takeNextPlayable() } ?: return
        val episode = next.episode
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
        load(player, next)
    }

    /**
     * Next pressed with next / previous set to change episode: saves where this one is, then
     * starts the first episode in Up next (which leaves the queue). Main thread.
     */
    private fun startNextInQueue(player: Player) {
        saveNow(player)
        write {
            val next = withContext(Dispatchers.IO) { takeNextPlayable() } ?: return@write
            if (session == null) {
                withContext(Dispatchers.IO) { queue.playNext(next.episode.id) }
                return@write
            }
            load(player, next)
        }
    }

    /** Loads [next] at its resume position and its speed (else the current one), and plays it. Main thread. */
    private fun load(player: Player, next: NextEpisode) {
        val episode = next.episode
        val speed = next.speed ?: player.playbackParameters.speed
        player.setMediaItem(
            EpisodeMediaItems.build(episode, next.podcast, next.localFile),
            PlaybackRules.resumePositionMs(episode),
        )
        player.setPlaybackSpeed(speed)
        player.prepare()
        player.play()
    }

    /**
     * Takes queued ids until one still names an episode; null when the queue runs out.
     * Comes with the episode's downloaded file when there is one, and the speed to play it at.
     */
    private suspend fun takeNextPlayable(): NextEpisode? {
        while (true) {
            val id = queue.takeNext() ?: return null
            return playable(id) ?: continue
        }
    }

    /**
     * [episodeId]'s episode with its downloaded file when there is one and the speed to play it
     * at (the show's, else the last speed used); null when there's no such episode. IO dispatcher.
     */
    private suspend fun playable(episodeId: String): NextEpisode? {
        val episode = episodes.getEpisode(episodeId) ?: return null
        val podcast = episodes.getPodcast(episode.podcastUrl)
        val speed = (podcast?.speed ?: lastSpeedUsed())?.let(PlaybackSpeeds::clamp)
        return NextEpisode(episode, podcast, LocalAudio.fileFor(downloads, episodeId), speed)
    }

    /** Null when the setting can't be read (then the player's current speed is kept). */
    private suspend fun lastSpeedUsed(): Float? =
        try {
            settings.speed.first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the speed", e)
            null
        }

    private class NextEpisode(
        val episode: Episode,
        val podcast: Podcast?,
        val localFile: File?,
        val speed: Float?,
    )

    /**
     * Android Auto's browse tree, and turning what a controller asks to play into items the
     * player can load. Every call arrives on the main thread; database reads run on IO.
     */
    private inner class LibraryCallback(private val player: Player) : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = CarMediaItems.folder(this@PlaybackService, CarBrowse.ROOT)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN))
            val rootParams = LibraryParams.Builder().setExtras(CarMediaItems.rootExtras()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            try {
                val item = itemFor(mediaId)
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't look up $mediaId for the car", e)
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
            }
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                val children = childrenOf(parentId, page, pageSize)
                if (children != null) {
                    LibraryResult.ofItemList(children, params)
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't list $parentId for the car", e)
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            // The app's own items carry their audio URI: today's path, no database read.
            if (mediaItems.all(EpisodeMediaItems::hasAudioUri)) {
                return Futures.immediateFuture(mediaItems.map(EpisodeMediaItems::resolve).toMutableList())
            }
            return scope.future {
                val resolved = mediaItems.mapNotNull { resolveItem(it)?.item }
                if (resolved.isEmpty()) throw UnsupportedOperationException("Nothing playable to add")
                resolved.toMutableList()
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all(EpisodeMediaItems::hasAudioUri)) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        mediaItems.map(EpisodeMediaItems::resolve),
                        startIndex,
                        startPositionMs,
                    ),
                )
            }
            return scope.future {
                val wanted = if (startIndex in mediaItems.indices) startIndex else 0
                val items = ArrayList<MediaItem>(mediaItems.size)
                var newStartIndex = 0
                var start: Resolved? = null
                mediaItems.forEachIndexed { index, item ->
                    if (index == wanted) newStartIndex = items.size
                    val resolved = resolveItem(item) ?: return@forEachIndexed
                    if (index == wanted) start = resolved
                    items += resolved.item
                }
                if (items.isEmpty()) throw UnsupportedOperationException("Nothing playable to set")
                newStartIndex = newStartIndex.coerceAtMost(items.lastIndex)

                val startItem = start
                val startEpisode = startItem?.episode
                val positionMs = if (startEpisode == null) {
                    // The start item had its URI (or was dropped): keep what was asked for.
                    if (startItem != null) startPositionMs else C.TIME_UNSET
                } else if (startPositionMs == C.TIME_UNSET) {
                    PlaybackRules.resumePositionMs(startEpisode.episode)
                } else {
                    startPositionMs
                }
                if (startEpisode != null) {
                    // Like the app's play: keep where the current one was, use the show's speed,
                    // and the started episode leaves Up next.
                    saveNow(player)
                    startEpisode.speed?.let(player::setPlaybackSpeed)
                    val id = startEpisode.episode.id
                    write { withContext(Dispatchers.IO) { queue.remove(id) } }
                }
                MediaSession.MediaItemsWithStartPosition(items, newStartIndex, positionMs)
            }
        }
    }

    /** An item ready for the player; [episode] is set when it was built from the database. */
    private class Resolved(val item: MediaItem, val episode: NextEpisode?)

    /**
     * [item] as the player can load it: an item with its audio URI as it is, an id-only item
     * (from the car) built from the database like the app's play. Null for folders, unknown ids
     * and failed lookups.
     */
    private suspend fun resolveItem(item: MediaItem): Resolved? {
        if (EpisodeMediaItems.hasAudioUri(item)) return Resolved(EpisodeMediaItems.resolve(item), null)
        val id = item.mediaId
        if (id.isEmpty() || CarBrowse.isFolder(id)) return null
        return try {
            val next = withContext(Dispatchers.IO) { playable(id) } ?: return null
            Resolved(EpisodeMediaItems.build(next.episode, next.podcast, next.localFile), next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't look up episode $id", e)
            null
        }
    }

    /** The browse item [mediaId] names: a folder, a show or an episode; null if there's none. */
    private suspend fun itemFor(mediaId: String): MediaItem? {
        CarMediaItems.folder(this, mediaId)?.let { return it }
        val feedUrl = CarBrowse.feedUrlOf(mediaId)
        if (feedUrl != null) {
            return withContext(Dispatchers.IO) { podcasts.podcast(feedUrl).first() }?.let(CarMediaItems::show)
        }
        if (mediaId.startsWith(CarBrowse.SHOW_PREFIX)) return null
        return withContext(Dispatchers.IO) {
            val episode = episodes.getEpisode(mediaId) ?: return@withContext null
            CarMediaItems.episode(episode, episodes.getPodcast(episode.podcastUrl))
        }
    }

    /** Page [page] of [parentId]'s children (at most [CarBrowse.MAX_ITEMS] in all); null for an unknown parent. */
    private suspend fun childrenOf(parentId: String, page: Int, pageSize: Int): List<MediaItem>? {
        if (parentId == CarBrowse.ROOT) {
            return CarMediaItems.page(CarMediaItems.topFolders(this), page, pageSize)
        }
        return withContext(Dispatchers.IO) {
            when (parentId) {
                CarBrowse.CONTINUE -> CarMediaItems.page(episodes.continueListening.first(), page, pageSize)
                    .map { CarMediaItems.episode(it.episode, it.podcastTitle, it.podcastArtworkUrl) }
                CarBrowse.UP_NEXT -> CarMediaItems.page(queue.queue.first(), page, pageSize)
                    .map { CarMediaItems.episode(it.episode, it.podcastTitle, it.podcastArtworkUrl) }
                CarBrowse.NEW -> CarMediaItems.page(episodes.newEpisodes.first(), page, pageSize)
                    .map { CarMediaItems.episode(it.episode, it.podcastTitle, it.podcastArtworkUrl) }
                CarBrowse.SHOWS -> CarMediaItems.page(podcasts.podcasts.first(), page, pageSize)
                    .map(CarMediaItems::show)
                else -> {
                    val feedUrl = CarBrowse.feedUrlOf(parentId) ?: return@withContext null
                    val podcast = podcasts.podcast(feedUrl).first()
                    // Newest first, as the repository returns them.
                    CarMediaItems.page(podcasts.episodes(feedUrl).first(), page, pageSize)
                        .map { CarMediaItems.episode(it, podcast) }
                }
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val FADE_STEP_MS = 250L
        const val MAX_WAIT_MS = 60_000L
    }
}
