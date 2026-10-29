package com.podparar.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import com.podparar.core.model.AppUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads an update and gives it to Android's own installer, which shows its usual
 * "update this app?" screen. Only builds signed with the same key can install over
 * this one, so a tampered download is refused by Android itself.
 */
@Singleton
class UpdateInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    /** Android wants the person to allow this app to install updates, once, in Settings. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads with progress (0..1), then starts the install. */
    suspend fun downloadAndInstall(update: AppUpdate, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val apk = File(context.cacheDir, "updates/PodOuvir.apk").apply { parentFile?.mkdirs() }
        download(update.downloadUrl, apk, update.sizeBytes, onProgress)
        install(apk)
    }

    private fun download(url: String, target: File, expectedSize: Long, onProgress: (Float) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Download failed: ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_ENTRY, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(context, sessionId, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val BUFFER = 64 * 1024
        const val APK_ENTRY = "PodOuvir.apk"
    }
}

/** Android reports back here; when it needs the person's OK it hands over its confirmation screen to show. */
internal class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Log.w("PodOuvir", "Update not installed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}
