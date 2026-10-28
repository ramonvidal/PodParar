package com.podparar.core.network

import com.podparar.core.model.LoadError
import com.podparar.core.model.PodcastException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Streams audio with OkHttp into a temporary file next to the target, then renames it into place.
 * The client's read timeout applies per read, so long transfers are fine (no call timeout is set).
 */
internal class OkHttpAudioDownloader(
    private val client: OkHttpClient,
    private val nanoTime: () -> Long = System::nanoTime,
) : AudioDownloader {

    override suspend fun download(url: String, target: File, onProgress: (Long, Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            val httpUrl = url.toHttpUrlOrNull() ?: throw PodcastException(LoadError.INVALID_LINK)
            val request = Request.Builder().url(httpUrl).get().build()
            val call = client.newCall(request)
            // Cancelling the coroutine cancels the call, which unblocks a read in progress.
            val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".part")
            var finished = false
            try {
                call.execute().use { response ->
                    when {
                        response.code == 404 || response.code == 410 -> throw PodcastException(LoadError.NOT_FOUND)
                        !response.isSuccessful -> throw PodcastException(LoadError.UNKNOWN)
                    }
                    val body = response.body ?: throw PodcastException(LoadError.UNKNOWN)
                    val total = body.contentLength().coerceAtLeast(0L)
                    var bytes = 0L
                    var lastReport = nanoTime()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                bytes += read
                                val now = nanoTime()
                                if (now - lastReport >= PROGRESS_INTERVAL_NS) {
                                    lastReport = now
                                    onProgress(bytes, total)
                                }
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    moveIntoPlace(temp, target)
                    finished = true
                    onProgress(bytes, total)
                }
            } catch (e: PodcastException) {
                throw e
            } catch (e: IOException) {
                // A cancelled call surfaces as an IOException; report it as cancellation, not as offline.
                currentCoroutineContext().ensureActive()
                throw PodcastException(LoadError.OFFLINE, e)
            } finally {
                cancelHandle?.dispose()
                if (!finished) {
                    temp.delete()
                }
            }
        }

    private fun moveIntoPlace(temp: File, target: File) {
        if (target.exists() && !target.delete()) throw IOException("Cannot replace $target")
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_NS = 500_000_000L
    }
}
