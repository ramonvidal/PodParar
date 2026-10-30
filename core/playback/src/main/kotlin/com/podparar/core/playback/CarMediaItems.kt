package com.podparar.core.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.podparar.core.model.CarBrowse
import com.podparar.core.model.Episode
import com.podparar.core.model.EpisodeState
import com.podparar.core.model.PlaybackRules
import com.podparar.core.model.Podcast

/**
 * The Android Auto browse tree's items ([CarBrowse] ids). Browse items carry no audio URI: when the
 * car plays one, [PlaybackService] looks the episode up by its id and builds the full item, so the
 * download, the show's speed and the resume position apply.
 */
@OptIn(UnstableApi::class)
internal object CarMediaItems {

    /** Root and the four top folders: the root is titled after the app, the rest after their list. */
    fun folder(context: Context, id: String): MediaItem? {
        val title = when (id) {
            CarBrowse.ROOT -> R.string.playback_car_root
            CarBrowse.CONTINUE -> R.string.playback_car_continue
            CarBrowse.UP_NEXT -> R.string.playback_car_up_next
            CarBrowse.NEW -> R.string.playback_car_new
            CarBrowse.SHOWS -> R.string.playback_car_shows
            else -> return null
        }
        return browsable(id, context.getString(title), artworkUrl = null, MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
    }

    /** Root's children, in the order the car shows them. */
    fun topFolders(context: Context): List<MediaItem> =
        listOf(CarBrowse.CONTINUE, CarBrowse.UP_NEXT, CarBrowse.NEW, CarBrowse.SHOWS)
            .mapNotNull { folder(context, it) }

    /** One subscribed show, opening onto its episodes. */
    fun show(podcast: Podcast): MediaItem =
        browsable(CarBrowse.showId(podcast.feedUrl), podcast.title, podcast.artworkUrl, MediaMetadata.MEDIA_TYPE_PODCAST)

    /** A playable episode: title, the show's title under it, the episode's image else the show's. */
    fun episode(episode: Episode, podcastTitle: String?, podcastArtworkUrl: String?): MediaItem {
        val artwork = episode.imageUrl?.takeIf { it.isNotBlank() } ?: podcastArtworkUrl?.takeIf { it.isNotBlank() }
        val extras = Bundle().apply {
            putString(EpisodeMediaItems.EXTRA_PODCAST_URL, episode.podcastUrl)
            putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, completionStatus(episode))
            if (episode.state == EpisodeState.IN_PROGRESS && episode.durationMs > 0) {
                putDouble(
                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                    PlaybackRules.progress(episode.positionMs, episode.durationMs).toDouble(),
                )
            }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(podcastTitle)
            .setSubtitle(podcastTitle)
            .setArtworkUri(artwork?.let(Uri::parse))
            .setDurationMs(episode.durationMs.takeIf { it > 0 })
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setMediaMetadata(metadata)
            .build()
    }

    fun episode(episode: Episode, podcast: Podcast?): MediaItem =
        episode(episode, podcast?.title, podcast?.artworkUrl)

    /**
     * Page [page] of [items] cut to [CarBrowse.MAX_ITEMS]; empty past the end.
     * [pageSize] is at least 1 (Media3 guarantees it; anything less is treated as 1).
     */
    fun <T> page(items: List<T>, page: Int, pageSize: Int): List<T> {
        val limited = items.take(CarBrowse.MAX_ITEMS)
        val size = pageSize.coerceAtLeast(1).toLong()
        val from = page.coerceAtLeast(0).toLong() * size
        if (from >= limited.size) return emptyList()
        val to = minOf(from + size, limited.size.toLong())
        return limited.subList(from.toInt(), to.toInt())
    }

    /** Content style hints for the car: episodes and folders as list rows. */
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    private fun completionStatus(episode: Episode): Int = when (episode.state) {
        EpisodeState.NEW -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
        EpisodeState.IN_PROGRESS -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
        EpisodeState.PLAYED -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
    }

    private fun browsable(id: String, title: String, artworkUrl: String?, mediaType: Int): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtworkUri(artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse))
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .setMediaType(mediaType)
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
}
