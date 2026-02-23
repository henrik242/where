package no.synth.where.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.data.RegionTileInfo
import no.synth.where.resources.Res
import no.synth.where.resources.ic_arrow_back
import no.synth.where.resources.ic_close
import no.synth.where.util.formatBytes
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexMapScreenContent(
    layerDisplayName: String,
    isDownloading: Boolean,
    downloadLayerId: String?,
    currentLayerId: String,
    downloadProgress: Int,
    selectedHexInfo: RegionTileInfo?,
    isHexSelected: Boolean,
    isHexDownloaded: Boolean,
    isHexPartiallyDownloaded: Boolean,
    offlineModeEnabled: Boolean,
    showDeleteDialog: Boolean,
    onBackClick: () -> Unit,
    onStopDownload: () -> Unit,
    onDownloadHex: () -> Unit,
    onDeleteHexRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onDismissHex: () -> Unit,
    mapContent: @Composable BoxScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(layerDisplayName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painterResource(Res.drawable.ic_arrow_back),
                            contentDescription = "Back"
                        )
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
            mapContent()

            // Download progress banner
            if (isDownloading && downloadLayerId == currentLayerId) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .align(Alignment.TopCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Downloading…", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$downloadProgress%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = onStopDownload) {
                                    Text("Stop")
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Hex action panel
            AnimatedVisibility(
                visible = isHexSelected,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Map area", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = onDismissHex) {
                                Icon(
                                    painterResource(Res.drawable.ic_close),
                                    contentDescription = "Dismiss"
                                )
                            }
                        }

                        when {
                            selectedHexInfo == null -> Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            isHexDownloaded -> Text(
                                "${selectedHexInfo.downloadedTiles} tiles · ${formatBytes(selectedHexInfo.downloadedSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            isHexPartiallyDownloaded -> Text(
                                "${selectedHexInfo.downloadedTiles} / ${selectedHexInfo.totalTiles} tiles downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> Text(
                                "Not downloaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isHexDownloaded || isHexPartiallyDownloaded) {
                                OutlinedButton(
                                    onClick = onDeleteHexRequest,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Delete")
                                }
                            }
                            if (!isHexDownloaded) {
                                Button(
                                    onClick = onDownloadHex,
                                    enabled = !isDownloading && !offlineModeEnabled
                                ) {
                                    Text(if (isHexPartiallyDownloaded) "Continue" else "Download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete tiles?") },
            text = { Text("Delete all downloaded map tiles for this area?") },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) { Text("Cancel") }
            }
        )
    }
}
