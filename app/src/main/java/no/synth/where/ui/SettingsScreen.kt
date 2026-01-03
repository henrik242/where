package no.synth.where.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.synth.where.data.ClientIdManager
import no.synth.where.data.UserPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onTracksClick: () -> Unit,
    showCountyBorders: Boolean,
    onShowCountyBordersChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val clientIdManager = ClientIdManager.getInstance(context)
        clientId = clientIdManager.getClientId()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // County borders toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Show County Borders",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = showCountyBorders,
                    onCheckedChange = onShowCountyBordersChange
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Online Tracking",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Share your location: $clientId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = userPreferences.onlineTrackingEnabled,
                    onCheckedChange = { userPreferences.updateOnlineTrackingEnabled(it) }
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
                    text = "Download Manager",
                    style = MaterialTheme.typography.bodyLarge
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Go to Download Manager"
                )
            }

            HorizontalDivider()
        }
    }
}

