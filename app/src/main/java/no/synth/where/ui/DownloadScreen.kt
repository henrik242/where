package no.synth.where.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val regions = remember { RegionsRepository.getRegions(context) }

    var downloadingRegion by remember { mutableStateOf<Region?>(null) }
    var downloadProgress by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<Region?>(null) }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val (totalSize, tileCount) = downloadManager.getTotalCacheInfo()
                    if (tileCount > 0) {
                        Text("Offline Maps • ${formatBytes(totalSize)} • $tileCount tiles")
                    } else {
                        Text("Download Offline Maps")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val (totalSize, tileCount) = downloadManager.getTotalCacheInfo()
                    if (tileCount > 0) {
                        IconButton(onClick = {
                            val baseDir = File(context.getExternalFilesDir(null), "tiles/kartverket")
                            val metadataFile = File(context.getExternalFilesDir(null), "tiles/metadata.json")
                            baseDir.deleteRecursively()
                            metadataFile.delete()
                            refreshTrigger++
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete All")
                }
            }
        }
    )

    // Delete confirmation dialog
    showDeleteDialog?.let { region ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${region.name}?") },
            text = { Text("This will delete all offline tiles for ${region.name}. You can re-download them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteRegionTiles(region)
                        showDeleteDialog = null
                        refreshTrigger++
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (downloadingRegion != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Downloading ${downloadingRegion?.name}...")
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { downloadProgress / 100f })
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$downloadProgress%")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(regions) { region ->
                        // Force recomposition when refreshTrigger changes
                        @Suppress("UNUSED_VARIABLE")
                        val trigger = refreshTrigger

                        val tileInfo = downloadManager.getRegionTileInfo(region)
                        val isDownloaded = tileInfo.isFullyDownloaded
                        val hasPartialDownload = tileInfo.downloadedTiles > 0

                        ListItem(
                            headlineContent = { Text(region.name) },
                            supportingContent = {
                                if (isDownloaded) {
                                    Text("✓ Downloaded • ${tileInfo.downloadedTiles} tiles • ${formatBytes(tileInfo.downloadedSize)}")
                                } else if (hasPartialDownload) {
                                    Text("${tileInfo.downloadedTiles}/${tileInfo.totalTiles} tiles • ${formatBytes(tileInfo.downloadedSize)}")
                                } else {
                                    Text("${tileInfo.totalTiles} tiles needed for offline use")
                                }
                            },
                            trailingContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasPartialDownload || isDownloaded) {
                                        IconButton(
                                            onClick = { showDeleteDialog = region }
                                        ) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            downloadingRegion = region
                                            scope.launch {
                                                downloadManager.downloadRegion(
                                                    region = region,
                                                    minZoom = 5,
                                                    maxZoom = 12,
                                                    onProgress = { progress ->
                                                        downloadProgress = progress
                                                    },
                                                    onComplete = { success ->
                                                        downloadingRegion = null
                                                        downloadProgress = 0
                                                        refreshTrigger++
                                                    }
                                                )
                                            }
                                        }
                                    ) {
                                        Text(
                                            when {
                                                isDownloaded -> "✓ Done"
                                                hasPartialDownload -> "Continue"
                                                else -> "Download"
                                            }
                                        )
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

