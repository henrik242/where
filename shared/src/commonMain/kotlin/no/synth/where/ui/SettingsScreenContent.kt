package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import no.synth.where.resources.Res
import no.synth.where.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class LanguageOption(val tag: String?, val displayName: String)

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
    onCrashReportingChange: (Boolean) -> Unit = {},
    currentLanguageLabel: String = "",
    languages: List<LanguageOption> = emptyList(),
    onLanguageSelected: (String?) -> Unit = {},
    currentThemeLabel: String = "",
    themeOptions: List<LanguageOption> = emptyList(),
    onThemeSelected: (String) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(Res.drawable.ic_arrow_back), contentDescription = stringResource(Res.string.back))
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOnlineTrackingClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.online_tracking),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(Res.string.go_to_online_tracking)
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
                        text = stringResource(Res.string.saved_tracks),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(Res.string.go_to_saved_tracks)
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
                        text = stringResource(Res.string.saved_points),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(Res.string.go_to_saved_points)
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDownloadClick() }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.offline_maps),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        painterResource(Res.drawable.ic_chevron_right),
                        contentDescription = stringResource(Res.string.go_to_download_manager)
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
                            text = stringResource(Res.string.crash_reporting),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(Res.string.crash_reporting_description),
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

                if (languages.isNotEmpty()) {
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
                                text = stringResource(Res.string.language),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentLanguageLabel,
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
                                        onLanguageSelected(option.tag)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                }

                if (themeOptions.isNotEmpty()) {
                    var themeExpanded by remember { mutableStateOf(false) }

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { themeExpanded = true }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.theme),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = currentThemeLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false }
                        ) {
                            themeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        themeExpanded = false
                                        option.tag?.let { onThemeSelected(it) }
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                }
            }

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
