package no.synth.where.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.synth.where.BuildConfig

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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                        text = "Online Tracking",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Go to Online Tracking"
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
                        text = "Saved Tracks",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Go to Saved Tracks"
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
                        text = "Saved Points",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Go to Saved Points"
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
                        text = "Offline Maps",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Go to Download Manager"
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
                            text = "Crash Reporting",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Send anonymous crash reports to help improve the app",
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
