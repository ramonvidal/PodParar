package com.podparar.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.data.PodcastRepository
import com.podparar.core.model.ImportResult
import com.podparar.core.model.LoadError
import com.podparar.core.model.Opml
import com.podparar.core.model.PodcastException
import com.podparar.core.settings.DownloadSettings
import com.podparar.core.settings.PlaybackSettings
import com.podparar.core.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the last OPML import or export got to. The screen turns it into words. */
internal sealed interface OpmlStatus {
    data class Importing(val done: Int, val total: Int) : OpmlStatus
    data object Exporting : OpmlStatus
    data class Imported(val result: ImportResult) : OpmlStatus
    data class Exported(val count: Int) : OpmlStatus

    /** The file wasn't an OPML list of shows. */
    data object NotOpml : OpmlStatus
    data object ImportFailed : OpmlStatus
    data object ExportFailed : OpmlStatus

    val isBusy: Boolean get() = this is Importing || this is Exporting
}

/** How "Check for updates" went. The screen turns it into words. */
internal sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck

    /** [version] is offered; the banner at the top of the screen shows it too. */
    data class Found(val version: String) : UpdateCheck
    data object Failed : UpdateCheck
}

/** The settings as stored on this phone, without the OPML message or the app version (the screen adds those). */
internal data class StoredSettings(
    val skipBackMs: Long,
    val skipForwardMs: Long,
    val skipSilence: Boolean,
    val shakeToExtend: Boolean,
    val nextPreviousSkip: Boolean,
    val wifiOnly: Boolean,
    val newEpisodeAlerts: Boolean,
)

/** This phone's choices, OPML import/export and "Check for updates". File access stays in the screen; this only sees text. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playback: PlaybackSettings,
    private val downloads: DownloadSettings,
    private val repository: PodcastRepository,
    private val updates: UpdateRepository,
) : ViewModel() {

    private val opmlStatus = MutableStateFlow<OpmlStatus?>(null)

    internal val settings: StateFlow<StoredSettings?> = combine(
        combine(playback.skipBackMs, playback.skipForwardMs, ::Pair),
        combine(playback.skipSilence, playback.shakeToExtend, ::Pair),
        playback.nextPreviousSkip,
        combine(downloads.wifiOnly, downloads.newEpisodeAlerts, ::Pair),
    ) { (back, forward), (silence, shake), nextPreviousSkip, (wifi, alerts) ->
        StoredSettings(back, forward, silence, shake, nextPreviousSkip, wifi, alerts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    internal val opml: StateFlow<OpmlStatus?> = opmlStatus

    private val _updateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    internal val updateCheck: StateFlow<UpdateCheck> = _updateCheck

    /** Asks the release page right away; a version put off with "Later" is offered again. */
    fun checkForUpdates() {
        if (_updateCheck.value == UpdateCheck.Checking) return
        _updateCheck.value = UpdateCheck.Checking
        viewModelScope.launch {
            _updateCheck.value = try {
                updates.checkNow()?.let { UpdateCheck.Found(it.versionName) } ?: UpdateCheck.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateCheck.Failed
            }
        }
    }

    fun setSkipBackMs(ms: Long) {
        viewModelScope.launch { playback.setSkipBackMs(ms) }
    }

    fun setSkipForwardMs(ms: Long) {
        viewModelScope.launch { playback.setSkipForwardMs(ms) }
    }

    fun setSkipSilence(enabled: Boolean) {
        viewModelScope.launch { playback.setSkipSilence(enabled) }
    }

    fun setShakeToExtend(enabled: Boolean) {
        viewModelScope.launch { playback.setShakeToExtend(enabled) }
    }

    fun setNextPreviousSkip(enabled: Boolean) {
        viewModelScope.launch { playback.setNextPreviousSkip(enabled) }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { downloads.setWifiOnly(enabled) }
    }

    fun setNewEpisodeAlerts(enabled: Boolean) {
        viewModelScope.launch { downloads.setNewEpisodeAlerts(enabled) }
    }

    /** Reads the chosen file with [read] (on the IO dispatcher) and follows every show in it not followed yet. */
    internal fun importOpml(read: suspend () -> String) {
        if (opmlStatus.value?.isBusy == true) return
        opmlStatus.value = OpmlStatus.Importing(done = 0, total = 0)
        viewModelScope.launch {
            opmlStatus.value = try {
                val text = withContext(Dispatchers.IO) { read() }
                val result = repository.importOpml(text) { done, total ->
                    opmlStatus.value = OpmlStatus.Importing(done, total)
                }
                OpmlStatus.Imported(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PodcastException) {
                if (e.error == LoadError.NOT_A_FEED) OpmlStatus.NotOpml else OpmlStatus.ImportFailed
            } catch (e: Exception) {
                OpmlStatus.ImportFailed
            }
        }
    }

    /** Writes the followed shows as OPML with [write] (on the IO dispatcher). */
    internal fun exportOpml(write: suspend (String) -> Unit) {
        if (opmlStatus.value?.isBusy == true) return
        opmlStatus.value = OpmlStatus.Exporting
        viewModelScope.launch {
            opmlStatus.value = try {
                val text = repository.exportOpml()
                withContext(Dispatchers.IO) { write(text) }
                OpmlStatus.Exported(count = Opml.parse(text).size)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OpmlStatus.ExportFailed
            }
        }
    }
}
