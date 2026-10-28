package com.podparar.feature.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.podparar.core.model.SkipLengths
import com.podparar.core.ui.R as CoreUiR
import java.io.IOException

internal data class SettingsUiState(
    val skipBackMs: Long = SkipLengths.DEFAULT_BACK_MS,
    val skipForwardMs: Long = SkipLengths.DEFAULT_FORWARD_MS,
    val skipSilence: Boolean = false,
    val shakeToExtend: Boolean = true,
    val wifiOnly: Boolean = true,
    val newEpisodeAlerts: Boolean = true,
    /** The outcome of the last OPML import or export, e.g. "Added 12 shows, 3 already followed, 1 failed". */
    val opmlMessage: String? = null,
    val opmlBusy: Boolean = false,
    val appVersion: String = "",
)

private const val EXPORT_FILE_NAME = "podparar-subscriptions.opml"
private val OPML_MIME_TYPES = arrayOf("text/x-opml", "text/xml", "application/xml", "*/*")

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val opml by viewModel.opml.collectAsStateWithLifecycle()
    val appVersion = remember(context) { context.appVersion() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val resolver = context.applicationContext.contentResolver
            viewModel.importOpml {
                resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: throw IOException("Couldn't open $uri")
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) {
            val resolver = context.applicationContext.contentResolver
            viewModel.exportOpml { text ->
                resolver.openOutputStream(uri, "wt")?.use { it.write(text.encodeToByteArray()) }
                    ?: throw IOException("Couldn't open $uri")
            }
        }
    }

    val stored = settings
    val state = SettingsUiState(
        opmlMessage = opml?.let { opmlMessage(it) },
        opmlBusy = opml?.isBusy == true,
        appVersion = appVersion,
    ).let { base ->
        if (stored == null) {
            base
        } else {
            base.copy(
                skipBackMs = stored.skipBackMs,
                skipForwardMs = stored.skipForwardMs,
                skipSilence = stored.skipSilence,
                shakeToExtend = stored.shakeToExtend,
                wifiOnly = stored.wifiOnly,
                newEpisodeAlerts = stored.newEpisodeAlerts,
            )
        }
    }

    SettingsContent(
        state = state,
        onBack = onBack,
        onSkipBackChange = viewModel::setSkipBackMs,
        onSkipForwardChange = viewModel::setSkipForwardMs,
        onSkipSilenceChange = viewModel::setSkipSilence,
        onShakeToExtendChange = viewModel::setShakeToExtend,
        onWifiOnlyChange = viewModel::setWifiOnly,
        onNewEpisodeAlertsChange = viewModel::setNewEpisodeAlerts,
        onImportOpml = { importLauncher.launch(OPML_MIME_TYPES) },
        onExportOpml = { exportLauncher.launch(EXPORT_FILE_NAME) },
    )
}

private fun Context.appVersion(): String =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    } catch (e: Exception) {
        ""
    }

@Composable
private fun opmlMessage(status: OpmlStatus): String = when (status) {
    is OpmlStatus.Importing -> if (status.total > 0) {
        stringResource(R.string.settings_importing_progress, status.done, status.total)
    } else {
        stringResource(R.string.settings_importing)
    }
    OpmlStatus.Exporting -> stringResource(R.string.settings_exporting)
    is OpmlStatus.Imported -> {
        val result = status.result
        if (result.added + result.alreadySubscribed + result.failed == 0) {
            stringResource(R.string.settings_import_nothing)
        } else {
            buildList {
                add(pluralStringResource(R.plurals.settings_import_added, result.added, result.added))
                if (result.alreadySubscribed > 0) {
                    add(stringResource(R.string.settings_import_already, result.alreadySubscribed))
                }
                if (result.failed > 0) add(stringResource(R.string.settings_import_failed_count, result.failed))
            }.joinToString(", ")
        }
    }
    is OpmlStatus.Exported -> pluralStringResource(R.plurals.settings_exported, status.count, status.count)
    OpmlStatus.NotOpml -> stringResource(R.string.settings_not_opml)
    OpmlStatus.ImportFailed -> stringResource(R.string.settings_import_failed)
    OpmlStatus.ExportFailed -> stringResource(R.string.settings_export_failed)
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSkipBackChange: (Long) -> Unit,
    onSkipForwardChange: (Long) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onShakeToExtendChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onNewEpisodeAlertsChange: (Boolean) -> Unit,
    onImportOpml: () -> Unit,
    onExportOpml: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(CoreUiR.string.settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.settings_back),
                    )
                }
            },
            // The app's Scaffold already keeps clear of the status bar.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Group(stringResource(R.string.settings_playback)) {
                SkipChoice(
                    title = stringResource(R.string.settings_skip_back),
                    options = SkipLengths.backOptionsMs,
                    selected = state.skipBackMs,
                    onSelect = onSkipBackChange,
                )
                SkipChoice(
                    title = stringResource(R.string.settings_skip_forward),
                    options = SkipLengths.forwardOptionsMs,
                    selected = state.skipForwardMs,
                    onSelect = onSkipForwardChange,
                )
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_skip_silence),
                    hint = stringResource(R.string.settings_skip_silence_hint),
                    checked = state.skipSilence,
                    onChange = onSkipSilenceChange,
                )
            }
            Group(stringResource(CoreUiR.string.sleep_timer)) {
                SwitchRow(
                    title = stringResource(R.string.settings_shake_to_extend),
                    hint = stringResource(R.string.settings_shake_to_extend_hint),
                    checked = state.shakeToExtend,
                    onChange = onShakeToExtendChange,
                )
            }
            Group(stringResource(R.string.settings_downloads)) {
                SwitchRow(
                    title = stringResource(R.string.settings_wifi_only),
                    hint = stringResource(R.string.settings_wifi_only_hint),
                    checked = state.wifiOnly,
                    onChange = onWifiOnlyChange,
                )
                RowDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_new_episode_alerts),
                    hint = stringResource(R.string.settings_new_episode_alerts_hint),
                    checked = state.newEpisodeAlerts,
                    onChange = onNewEpisodeAlertsChange,
                )
            }
            Group(stringResource(R.string.settings_your_shows)) {
                ActionRow(
                    icon = CoreUiR.drawable.ic_file_upload,
                    title = stringResource(R.string.settings_import_opml),
                    hint = stringResource(R.string.settings_import_opml_hint),
                    enabled = !state.opmlBusy,
                    onClick = onImportOpml,
                )
                RowDivider()
                ActionRow(
                    icon = CoreUiR.drawable.ic_file_download,
                    title = stringResource(R.string.settings_export_opml),
                    hint = stringResource(R.string.settings_export_opml_hint),
                    enabled = !state.opmlBusy,
                    onClick = onExportOpml,
                )
                if (state.opmlBusy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
                if (state.opmlMessage != null) {
                    Text(
                        state.opmlMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Group(stringResource(R.string.settings_about)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        painterResource(CoreUiR.drawable.ic_info),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (state.appVersion.isBlank()) {
                            stringResource(R.string.settings_app_name)
                        } else {
                            stringResource(R.string.settings_app_version, state.appVersion)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

/** A titled soft card holding a few rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
        )
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SkipChoice(title: String, options: List<Long>, selected: Long, onSelect: (Long) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, ms ->
                SegmentedButton(
                    selected = ms == selected,
                    onClick = { onSelect(ms) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { Text(stringResource(R.string.settings_seconds, (ms / 1000).toInt())) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The whole row toggles, so the switch itself takes no separate tap.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(
    @DrawableRes icon: Int,
    title: String,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
