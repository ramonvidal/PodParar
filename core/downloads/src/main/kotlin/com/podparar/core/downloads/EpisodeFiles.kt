package com.podparar.core.downloads

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Where downloaded audio lives: filesDir/episodes/<sha1(episodeId)>.audio. */
internal object EpisodeFiles {
    private const val DIR = "episodes"
    private const val SUFFIX = ".audio"

    fun dir(context: Context): File = File(context.filesDir, DIR)

    fun fileFor(context: Context, episodeId: String): File = File(dir(context), sha1(episodeId) + SUFFIX)

    /** Deletes the episode's file, and [storedPath] too when the row points somewhere else. */
    fun delete(context: Context, episodeId: String, storedPath: String?) {
        val file = fileFor(context, episodeId)
        file.delete()
        if (storedPath != null && File(storedPath) != file) File(storedPath).delete()
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
