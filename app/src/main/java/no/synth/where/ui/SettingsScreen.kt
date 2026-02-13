package no.synth.where.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import no.synth.where.BuildConfig
import no.synth.where.R

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    crashReportingEnabled: Boolean,
    onCrashReportingChange: (Boolean) -> Unit
) {
    SettingsScreenContent(
        versionInfo = "${BuildConfig.GIT_COMMIT_COUNT}.${BuildConfig.GIT_SHORT_SHA} ${BuildConfig.BUILD_DATE}",
        onBackClick = onBackClick,
        onDownloadClick = onDownloadClick,
        onTracksClick = onTracksClick,
        onSavedPointsClick = onSavedPointsClick,
        onOnlineTrackingClick = onOnlineTrackingClick,
        crashReportingEnabled = crashReportingEnabled,
        onCrashReportingChange = onCrashReportingChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    versionInfo: String,
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit,
    crashReportingEnabled: Boolean = false,
    onCrashReportingChange: (Boolean) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Online Tracking option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOnlineTrackingClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.online_tracking),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.go_to_online_tracking)
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTracksClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.saved_tracks),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.go_to_saved_tracks)
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSavedPointsClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.saved_points),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.go_to_saved_points)
                    )
                }

                HorizontalDivider()

                // Download Manager option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDownloadClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.offline_maps),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.go_to_download_manager)
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCrashReportingChange(!crashReportingEnabled) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.crash_reporting),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.crash_reporting_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        modifier = Modifier.padding(start = 16.dp),
                        checked = crashReportingEnabled,
                        onCheckedChange = onCrashReportingChange
                    )
                }

                HorizontalDivider()

                LanguageSelector()

                HorizontalDivider()
            }

            // Version number at the bottom
            Text(
                text = versionInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

private data class LanguageOption(val tag: String?, val displayName: String)

@Composable
private fun LanguageSelector() {
    val languages = listOf(
        LanguageOption(null, stringResource(R.string.system_default)),
        LanguageOption("en", "English"),
        LanguageOption("nb", "Norsk bokmål")
    )

    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (currentLocale.isEmpty) null else currentLocale.toLanguageTags()
    val currentLabel = languages.find { it.tag == currentTag }?.displayName
        ?: languages.first().displayName

    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.language),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languages.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = {
                        expanded = false
                        val locales = if (option.tag == null) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(option.tag)
                        }
                        AppCompatDelegate.setApplicationLocales(locales)
                    }
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreenContent(
        versionInfo = "42.abc1234 2026-02-06",
        onBackClick = {},
        onDownloadClick = {},
        onTracksClick = {},
        onSavedPointsClick = {},
        onOnlineTrackingClick = {}
    )
}
