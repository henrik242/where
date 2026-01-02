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

    // Calculate download statistics for the entire kartverket tiles directory
    fun getTotalDownloadInfo(): Pair<Boolean, Long> {
        val baseDir = File(context.getExternalFilesDir(null), "tiles/kartverket")
        if (!baseDir.exists()) return Pair(false, 0L)

        var totalSize = 0L
        var tileCount = 0
        baseDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "png") {
                totalSize += file.length()
                tileCount++
            }
        }
        return Pair(tileCount > 0, totalSize)
    }


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
                    val (hasDownloads, totalSize) = getTotalDownloadInfo()
                    if (hasDownloads) {
                        Text("Offline Maps • ${formatBytes(totalSize)}")
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
                    val (hasDownloads, _) = getTotalDownloadInfo()
                    if (hasDownloads) {
                        IconButton(onClick = {
                            val baseDir = File(context.getExternalFilesDir(null), "tiles/kartverket")
                            baseDir.deleteRecursively()
                            refreshTrigger++
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete All")
                        }
                    }
                }
            )
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
                                Button(onClick = {
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
                                }) {
                                    Text(
                                        when {
                                            isDownloaded -> "✓ Done"
                                            hasPartialDownload -> "Continue"
                                            else -> "Download"
                                        }
                                    )
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

