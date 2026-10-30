package com.podparar.core.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.podparar.core.model.Episode
import com.podparar.core.model.NowPlaying
import com.podparar.core.model.Podcast
import java.io.File

/** Translation between `:core:model` types and Media3 items. Media3 stays inside this module. */
@OptIn(UnstableApi::class)
internal object EpisodeMediaItems {
    /** Key in [MediaMetadata.extras] holding the episode's podcast feed URL. */
    const val EXTRA_PODCAST_URL = "podcastUrl"

    /**
     * mediaId = episode id. The URI is set twice: as the local configuration and as
     * [MediaItem.RequestMetadata.mediaUri], because the local configuration doesn't cross
     * from a MediaController to the session; the service rebuilds it from the request metadata.
     * With a [localFile] (a finished download), that file is played instead of the audio URL.
     */
    fun build(episode: Episode, podcast: Podcast?, localFile: File? = null): MediaItem {
        val audioUri = localFile?.let(Uri::fromFile) ?: Uri.parse(episode.audioUrl)
        val artwork = episode.imageUrl ?: podcast?.artworkUrl
        val extras = Bundle().apply { putString(EXTRA_PODCAST_URL, episode.podcastUrl) }
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcast?.title)
            .setArtworkUri(artwork?.let(Uri::parse))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(audioUri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(audioUri).build(),
            )
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Whether [item] says where its audio is (an item the app's own controller sent). The car
     * sends only an episode id; those are looked up in the database instead.
     */
    fun hasAudioUri(item: MediaItem): Boolean =
        item.localConfiguration != null || item.requestMetadata.mediaUri != null

    /** Rebuilds an item that arrived from a controller so the player can load it. */
    fun resolve(item: MediaItem): MediaItem {
        val uri = item.requestMetadata.mediaUri ?: return item
        return item.buildUpon().setUri(uri).build()
    }

    /** The player's duration in ms, 0 when unknown. */
    fun durationOf(player: Player): Long {
        val duration = player.duration
        return if (duration == C.TIME_UNSET || duration < 0) 0L else duration
    }

    /** What the UI shows for [player]'s current item, or null when nothing is loaded. */
    fun nowPlaying(player: Player): NowPlaying? {
        val item = player.currentMediaItem ?: return null
        val metadata = item.mediaMetadata
        val durationMs = durationOf(player)
        val position = player.currentPosition.coerceAtLeast(0L)
        return NowPlaying(
            episodeId = item.mediaId,
            podcastUrl = metadata.extras?.getString(EXTRA_PODCAST_URL).orEmpty(),
            title = metadata.title?.toString().orEmpty(),
            podcastTitle = metadata.artist?.toString().orEmpty(),
            artworkUrl = metadata.artworkUri?.toString(),
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            positionMs = if (durationMs > 0) position.coerceAtMost(durationMs) else position,
            durationMs = durationMs,
            speed = player.playbackParameters.speed,
        )
    }
}
