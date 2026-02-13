package no.synth.where.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import org.koin.androidx.compose.koinViewModel
import no.synth.where.R

@Composable
fun OnlineTrackingScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val resources = context.resources
    val viewModel: OnlineTrackingScreenViewModel = koinViewModel()
    val isTrackingEnabled by viewModel.onlineTrackingEnabled.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val trackingServerUrl by viewModel.trackingServerUrl.collectAsState()
    var showRegenerateDialog by remember { mutableStateOf(false) }

    OnlineTrackingScreenContent(
        isTrackingEnabled = isTrackingEnabled,
        clientId = clientId,
        showRegenerateDialog = showRegenerateDialog,
        onBackClick = onBackClick,
        onToggleTracking = { viewModel.toggleTracking(it) },
        onViewOnWeb = {
            val url = "${trackingServerUrl}?clients=$clientId"
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        },
        onShare = {
            val url = "${trackingServerUrl}?clients=$clientId"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_tracking_message, url))
                putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.live_location_tracking))
            }
            context.startActivity(Intent.createChooser(shareIntent, resources.getString(R.string.share_tracking_chooser)))
        },
        onRegenerateClick = { showRegenerateDialog = true },
        onConfirmRegenerate = {
            viewModel.regenerateClientId()
            showRegenerateDialog = false
        },
        onDismissRegenerate = { showRegenerateDialog = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineTrackingScreenContent(
    isTrackingEnabled: Boolean,
    clientId: String,
    showRegenerateDialog: Boolean,
    onBackClick: () -> Unit,
    onToggleTracking: (Boolean) -> Unit,
    onViewOnWeb: () -> Unit,
    onShare: () -> Unit,
    onRegenerateClick: () -> Unit,
    onConfirmRegenerate: () -> Unit,
    onDismissRegenerate: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.online_tracking)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enable/Disable Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTrackingEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.enable_online_tracking),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isTrackingEnabled) stringResource(R.string.tracking_active) else stringResource(R.string.tracking_disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isTrackingEnabled,
                        onCheckedChange = onToggleTracking
                    )
                }
            }

            // Client ID Card
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.your_client_id),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = clientId,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.client_id_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Actions Section
            Text(
                text = stringResource(R.string.actions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // View on Web Button
            Button(
                onClick = onViewOnWeb,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.OpenInBrowser,
                    contentDescription = stringResource(R.string.open_in_browser),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.view_on_web))
            }

            // Share Button
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.share_tracking_link))
            }

            // Regenerate ID Button
            OutlinedButton(
                onClick = onRegenerateClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.regenerate_id),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.regenerate_client_id))
            }

            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.how_it_works),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.how_it_works_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }

    // Regenerate Dialog
    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = onDismissRegenerate,
            title = { Text(stringResource(R.string.regenerate_client_id_title)) },
            text = {
                Text(stringResource(R.string.regenerate_client_id_message, clientId))
            },
            confirmButton = {
                TextButton(onClick = onConfirmRegenerate) {
                    Text(stringResource(R.string.regenerate))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRegenerate) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun OnlineTrackingScreenPreview() {
    OnlineTrackingScreenContent(
        isTrackingEnabled = true,
        clientId = "ABCD-1234",
        showRegenerateDialog = false,
        onBackClick = {},
        onToggleTracking = {},
        onViewOnWeb = {},
        onShare = {},
        onRegenerateClick = {},
        onConfirmRegenerate = {},
        onDismissRegenerate = {}
    )
}
