package no.synth.where.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
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
    onSavedPointsClick: () -> Unit,
    showCountyBorders: Boolean,
    onShowCountyBordersChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val userPreferences = remember { UserPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf("") }
    var showRegenerateDialog by remember { mutableStateOf(false) }
    val clientIdManager = remember { ClientIdManager.getInstance(context) }

    LaunchedEffect(Unit) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Online Tracking",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Client ID: $clientId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = userPreferences.onlineTrackingEnabled,
                        onCheckedChange = { userPreferences.updateOnlineTrackingEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val url = "${userPreferences.trackingServerUrl}?clients=$clientId"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View on Web")
                    }

                    OutlinedButton(
                        onClick = { showRegenerateDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Regenerate ID",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New ID")
                    }
                }
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
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text("Regenerate Client ID?") },
            text = {
                Text("This will create a new client ID. Your old ID ($clientId) will no longer be associated with your tracks on the server.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            clientId = clientIdManager.regenerateClientId()
                            showRegenerateDialog = false
                        }
                    }
                ) {
                    Text("Regenerate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

