package no.synth.where.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.data.DownloadStatus
import no.synth.where.data.QueueSummary
import no.synth.where.data.QueuedDownload
import no.synth.where.data.RegionTileInfo
import no.synth.where.resources.Res
import no.synth.where.resources.*
import no.synth.where.ui.map.ZoomControls
import no.synth.where.util.formatBytes
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexMapScreenContent(
    layerDisplayName: String,
    selectedHexDownload: QueuedDownload?,
    queueSummary: QueueSummary,
    selectedHexInfo: RegionTileInfo?,
    selectedHexName: String?,
    isLoadingHexName: Boolean,
    isHexSelected: Boolean,
    isHexDownloaded: Boolean,
    isHexPartiallyDownloaded: Boolean,
    offlineModeEnabled: Boolean,
    showDeleteDialog: Boolean,
    onBackClick: () -> Unit,
    onCancelHexDownload: () -> Unit,
    onDownloadHex: () -> Unit,
    onDeleteHexRequest: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onOfflineChipClick: () -> Unit,
    onQueueChipClick: () -> Unit,
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
        // The always-visible compass sits top-right; reserve space so the offline chip clears it.
        val offlineChipEnd = 56.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            mapContent()

            // Zoom controls and offline chip stay live while downloads run in the background,
            // so the user can keep adding hexes to the queue.
            ZoomControls(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut
            )

            if (offlineModeEnabled) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = offlineChipEnd)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onOfflineChipClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_cloud_off),
                        contentDescription = stringResource(Res.string.offline_mode),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.offline_mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Compact queue overview chip while downloads are in flight; tap to open Downloads.
            if (queueSummary.total > 0 && !queueSummary.allDone) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onQueueChipClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    val activeName = queueSummary.activeName
                    Text(
                        text = if (activeName != null) {
                            stringResource(Res.string.downloading_region, activeName) +
                                " (${queueSummary.position}/${queueSummary.total})"
                        } else {
                            "${queueSummary.position}/${queueSummary.total}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(selectedHexName ?: "Map area", style = MaterialTheme.typography.titleMedium)
                                if (isLoadingHexName) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                            IconButton(onClick = onDismissHex) {
                                Icon(
                                    painterResource(Res.drawable.ic_close),
                                    contentDescription = "Dismiss"
                                )
                            }
                        }

                        val dl = selectedHexDownload
                        when {
                            dl != null && dl.status == DownloadStatus.DOWNLOADING -> {
                                LinearProgressIndicator(
                                    progress = { dl.overallProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    "${dl.mapProgress}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            dl != null && dl.status == DownloadStatus.QUEUED -> Text(
                                stringResource(Res.string.queued),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            dl != null && dl.status == DownloadStatus.FAILED -> Text(
                                stringResource(Res.string.download_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            dl != null && dl.status == DownloadStatus.COMPLETED -> Text(
                                stringResource(Res.string.downloaded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
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
                                "Not downloaded · ~${selectedHexInfo.totalTiles} tiles",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val activeInQueue = dl?.status == DownloadStatus.QUEUED || dl?.status == DownloadStatus.DOWNLOADING
                        val isCompleted = dl?.status == DownloadStatus.COMPLETED
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isHexDownloaded || isHexPartiallyDownloaded || isCompleted) {
                                OutlinedButton(
                                    onClick = onDeleteHexRequest,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text(stringResource(Res.string.delete))
                                }
                            }
                            if (activeInQueue && dl != null) {
                                Button(onClick = onCancelHexDownload) {
                                    Text(if (dl.status == DownloadStatus.DOWNLOADING) stringResource(Res.string.stop) else stringResource(Res.string.cancel))
                                }
                            } else if (!isHexDownloaded && !isCompleted) {
                                Button(
                                    onClick = onDownloadHex,
                                    enabled = !offlineModeEnabled
                                ) {
                                    Text(if (isHexPartiallyDownloaded) stringResource(Res.string.continue_download) else stringResource(Res.string.download))
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
