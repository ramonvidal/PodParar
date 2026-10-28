package com.podparar.core.playback

import android.util.Log
import com.podparar.core.data.DownloadRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Finds the downloaded copy of an episode, so it plays with no signal. */
internal object LocalAudio {
    private const val TAG = "LocalAudio"

    /**
     * The downloaded file of [episodeId] when its download is DONE and the file still exists,
     * else null (stream the audio URL). Checked on the IO dispatcher; a failed lookup streams.
     */
    suspend fun fileFor(downloads: DownloadRepository, episodeId: String): File? =
        withContext(Dispatchers.IO) {
            try {
                downloads.localPath(episodeId)
                    ?.let(::File)
                    ?.takeIf { it.isFile }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't look up the download", e)
                null
            }
        }
}
