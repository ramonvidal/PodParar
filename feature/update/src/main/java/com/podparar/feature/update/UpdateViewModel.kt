package com.podparar.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podparar.core.model.AppUpdate
import com.podparar.core.update.UpdateInstaller
import com.podparar.core.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the banner shows. */
sealed interface UpdateState {
    data object None : UpdateState
    data class Available(val update: AppUpdate) : UpdateState
    data class NeedsPermission(val update: AppUpdate) : UpdateState
    data class Downloading(val update: AppUpdate, val progress: Float) : UpdateState
    data class Failed(val update: AppUpdate) : UpdateState
}

/** Offers a newer build from the release page, downloads it and hands it to Android's installer. */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updates: UpdateRepository,
    private val installer: UpdateInstaller,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state

    init {
        viewModelScope.launch {
            updates.available.collect { update ->
                // A download or a trip to Settings in progress carries on.
                val current = _state.value
                if (current is UpdateState.None || current is UpdateState.Available) {
                    _state.value = update?.let(UpdateState::Available) ?: UpdateState.None
                }
            }
        }
    }

    fun install(update: AppUpdate) {
        if (!installer.canInstall()) {
            _state.value = UpdateState.NeedsPermission(update)
            return
        }
        _state.value = UpdateState.Downloading(update, 0f)
        viewModelScope.launch {
            _state.value = try {
                installer.downloadAndInstall(update) { progress -> _state.value = UpdateState.Downloading(update, progress) }
                // Android's own "update this app?" screen takes over from here.
                UpdateState.Available(update)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(update)
            }
        }
    }

    /** Each time the app is shown: checks when one is due, and carries on after "allow installs". */
    fun onResume() {
        viewModelScope.launch { updates.refresh() }
        val waiting = _state.value as? UpdateState.NeedsPermission ?: return
        if (installer.canInstall()) install(waiting.update)
    }

    fun allowInstallsIntent() = installer.allowInstallsIntent()

    fun later(update: AppUpdate) {
        updates.dismiss(update)
        _state.value = UpdateState.None
    }
}
