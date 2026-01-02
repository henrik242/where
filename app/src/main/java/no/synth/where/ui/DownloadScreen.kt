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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository
import java.io.File
import java.util.Locale

enum class DownloadTab {
    KARTVERKET,
    TOPORASTER,
    OSM,
    OPENTOPOMAP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val regions = remember { RegionsRepository.getRegions(context) }

    var selectedTab by remember { mutableStateOf(DownloadTab.KARTVERKET) }
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

    // Calculate per-layer statistics
    fun getLayerStats(layerName: String): Pair<Long, Int> {
        val layerDir = File(context.getExternalFilesDir(null), "tiles/$layerName")
        if (!layerDir.exists()) return Pair(0L, 0)

        var totalSize = 0L
        var tileCount = 0
        layerDir.walkTopDown().forEach { file ->
            if (file.isFile && file.extension == "png") {
                totalSize += file.length()
                tileCount++
            }
        }
        return Pair(totalSize, tileCount)
    }

    // Get MapLibre's automatic tile cache size
    fun getMapLibreCacheSize(): Long {
        var totalSize = 0L
        val cacheDir = context.cacheDir
        cacheDir.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        return totalSize
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline Maps") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "Map Layers Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Automatic cache card (MapLibre's on-the-fly cache)
                item {
                    val cacheSize = getMapLibreCacheSize()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Automatic Cache",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Tiles loaded automatically while browsing",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (cacheSize > 0) {
                                    Text(
                                        formatBytes(cacheSize),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                }

                // Kartverket layer card
                item {
                    val (size, count) = getLayerStats("kartverket")
                    LayerOverviewCard(
                        layerName = "Kartverket",
                        description = "Norwegian standard topographic maps",
                        tileCount = count,
                        totalSize = size,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.KARTVERKET,
                        onClick = { selectedTab = DownloadTab.KARTVERKET }
                    )
                }

                // Toporaster layer card
                item {
                    val (size, count) = getLayerStats("toporaster")
                    LayerOverviewCard(
                        layerName = "Toporaster",
                        description = "Norwegian hiking & outdoor maps",
                        tileCount = count,
                        totalSize = size,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.TOPORASTER,
                        onClick = { selectedTab = DownloadTab.TOPORASTER }
                    )
                }

                // OSM layer card
                item {
                    val (size, count) = getLayerStats("osm")
                    LayerOverviewCard(
                        layerName = "OpenStreetMap",
                        description = "Standard street maps",
                        tileCount = count,
                        totalSize = size,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.OSM,
                        onClick = { selectedTab = DownloadTab.OSM }
                    )
                }

                // OpenTopoMap layer card
                item {
                    val (size, count) = getLayerStats("opentopomap")
                    LayerOverviewCard(
                        layerName = "OpenTopoMap",
                        description = "International hiking maps",
                        tileCount = count,
                        totalSize = size,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.OPENTOPOMAP,
                        onClick = { selectedTab = DownloadTab.OPENTOPOMAP }
                    )
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                // Region management section based on selected tab
                item {
                    Text(
                        when (selectedTab) {
                            DownloadTab.KARTVERKET -> "Kartverket Regions"
                            DownloadTab.TOPORASTER -> "Toporaster Regions"
                            DownloadTab.OSM -> "OSM Regions"
                            DownloadTab.OPENTOPOMAP -> "OpenTopoMap Regions"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                }

                // Show downloading progress if active
                if (downloadingRegion != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Downloading ${downloadingRegion?.name}...")
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("$downloadProgress%", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // List regions for the selected layer
                items(regions) { region ->
                    @Suppress("UNUSED_VARIABLE")
                    val trigger = refreshTrigger

                    // Get layer name based on selected tab
                    val layerName = when (selectedTab) {
                        DownloadTab.KARTVERKET -> "kartverket"
                        DownloadTab.TOPORASTER -> "toporaster"
                        DownloadTab.OSM -> "osm"
                        DownloadTab.OPENTOPOMAP -> "opentopomap"
                    }

                    val tileInfo = downloadManager.getRegionTileInfo(region, layerName)
                    val isDownloaded = tileInfo.isFullyDownloaded
                    val hasPartialDownload = tileInfo.downloadedTiles > 0

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text(region.name) },
                            supportingContent = {
                                if (isDownloaded) {
                                    Text("✓ ${tileInfo.downloadedTiles} tiles • ${formatBytes(tileInfo.downloadedSize)}")
                                } else if (hasPartialDownload) {
                                    Text("${tileInfo.downloadedTiles}/${tileInfo.totalTiles} tiles • ${formatBytes(tileInfo.downloadedSize)}")
                                } else {
                                    Text("${tileInfo.totalTiles} tiles needed")
                                }
                            },
                            trailingContent = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (hasPartialDownload || isDownloaded) {
                                        IconButton(onClick = { showDeleteDialog = region }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            downloadingRegion = region
                                            scope.launch {
                                                downloadManager.downloadRegion(
                                                    region = region,
                                                    layerName = layerName,
                                                    minZoom = 5,
                                                    maxZoom = 12,
                                                    onProgress = { progress -> downloadProgress = progress },
                                                    onComplete = { _ ->
                                                        downloadingRegion = null
                                                        downloadProgress = 0
                                                        refreshTrigger++
                                                    }
                                                )
                                            }
                                        },
                                        enabled = downloadingRegion == null
                                    ) {
                                        Text(
                                            when {
                                                isDownloaded -> "✓"
                                                hasPartialDownload -> "Continue"
                                                else -> "Download"
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { region ->
        val layerName = when (selectedTab) {
            DownloadTab.KARTVERKET -> "kartverket"
            DownloadTab.TOPORASTER -> "toporaster"
            DownloadTab.OSM -> "osm"
            DownloadTab.OPENTOPOMAP -> "opentopomap"
        }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${region.name}?") },
            text = { Text("This will delete all $layerName tiles for ${region.name}. You can re-download them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        downloadManager.deleteRegionTiles(region, layerName)
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

@Composable
fun LayerOverviewCard(
    layerName: String,
    description: String,
    tileCount: Int,
    totalSize: Long,
    formatBytes: (Long) -> String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSelected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        layerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tileCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            formatBytes(totalSize),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$tileCount tiles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        "Not downloaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
