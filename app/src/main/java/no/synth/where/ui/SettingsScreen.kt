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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import no.synth.where.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    onSavedPointsClick: () -> Unit,
    onOnlineTrackingClick: () -> Unit
) {
    val context = LocalContext.current

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
            }

            // Version number at the bottom
            Text(
                text = "${BuildConfig.GIT_COMMIT_COUNT}.${BuildConfig.GIT_SHORT_SHA} ${BuildConfig.BUILD_DATE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

