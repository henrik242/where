package no.synth.where.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.Region
import no.synth.where.data.RegionsRepository
import no.synth.where.service.MapDownloadService
import org.maplibre.android.geometry.LatLngBounds
import java.io.File
import java.util.Locale

data class LayerInfo(
    val id: String,
    val displayName: String,
    val description: String
)

@Composable
fun DownloadScreen(
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit
) {
    val context = LocalContext.current
    val downloadManager = remember { MapDownloadManager(context) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var cacheSize by remember { mutableStateOf(0L) }
    val downloadState by MapDownloadService.downloadState.collectAsState()

    val layers = remember {
        listOf(
            LayerInfo("kartverket", "Kartverket", "Topographic maps from Kartverket"),
            LayerInfo("toporaster", "Kartverket Toporaster", "Topographic raster maps"),
            LayerInfo("sjokartraster", "Kartverket Sjøkart", "Nautical charts"),
            LayerInfo("osm", "OpenStreetMap", "Community-sourced street maps"),
            LayerInfo("opentopomap", "OpenTopoMap", "Topographic maps with hiking trails"),
            LayerInfo("waymarkedtrails", "Waymarked Trails", "Hiking trail overlay")
        )
    }

    // Calculate cache size on screen load and when refresh is triggered
    LaunchedEffect(refreshTrigger) {
        val maplibreTilesDir = File(context.getExternalFilesDir(null), "maplibre-tiles")
        cacheSize = if (maplibreTilesDir.exists()) {
            maplibreTilesDir.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0L
            }
        } else {
            context.cacheDir.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0L
            }
        }
    }

    DownloadScreenContent(
        layers = layers,
        cacheSize = cacheSize,
        isDownloading = downloadState.isDownloading,
        downloadRegionName = downloadState.region?.name,
        downloadLayerName = downloadState.layerName,
        downloadProgress = downloadState.progress,
        onBackClick = onBackClick,
        onLayerClick = onLayerClick,
        onStopDownload = { MapDownloadService.stopDownload(context) },
        getLayerStats = { layerName -> downloadManager.getLayerStats(layerName) },
        refreshTrigger = refreshTrigger
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreenContent(
    layers: List<LayerInfo>,
    cacheSize: Long,
    isDownloading: Boolean,
    downloadRegionName: String?,
    downloadLayerName: String?,
    downloadProgress: Int,
    onBackClick: () -> Unit,
    onLayerClick: (String) -> Unit,
    onStopDownload: () -> Unit,
    getLayerStats: suspend (String) -> Pair<Long, Int>,
    refreshTrigger: Int
) {
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
                title = { Text("Offline Maps") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Automatic cache card
            item {
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

            // Active download progress card
            if (isDownloading && downloadRegionName != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
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
                                        "Downloading $downloadRegionName",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        downloadLayerName ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = onStopDownload) {
                                    Text("Stop")
                                }
                            }
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

            // Layer cards
            items(layers) { layer ->
                var stats by remember { mutableStateOf(Pair(0L, 0)) }
                LaunchedEffect(refreshTrigger, layer.id) {
                    stats = getLayerStats(layer.id)
                }

                Card(
                    onClick = { onLayerClick(layer.id) },
                    modifier = Modifier.fillMaxWidth()
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
                                layer.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                layer.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (stats.second > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${stats.second} tiles • ${formatBytes(stats.first)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "View regions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LayerRegionsScreen(
    layerId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val regions = remember { RegionsRepository.getRegions(context) }

    val downloadState by MapDownloadService.downloadState.collectAsState()
    var refreshTrigger by remember { mutableStateOf(0) }
    var showDeleteDialog by remember { mutableStateOf<Region?>(null) }

    // Trigger refresh when download completes
    LaunchedEffect(downloadState.isDownloading) {
        if (!downloadState.isDownloading && downloadState.region != null) {
            refreshTrigger++
        }
    }

    val layerDisplayName = remember(layerId) {
        when (layerId) {
            "kartverket" -> "Kartverket"
            "toporaster" -> "Kartverket Toporaster"
            "sjokartraster" -> "Kartverket Sjøkart"
            "osm" -> "OpenStreetMap"
            "opentopomap" -> "OpenTopoMap"
            "waymarkedtrails" -> "Waymarked Trails"
            else -> layerId
        }
    }

    LayerRegionsScreenContent(
        layerDisplayName = layerDisplayName,
        layerId = layerId,
        regions = regions,
        isDownloading = downloadState.isDownloading,
        downloadRegionName = downloadState.region?.name,
        downloadLayerName = downloadState.layerName,
        downloadProgress = downloadState.progress,
        showDeleteDialog = showDeleteDialog,
        onBackClick = onBackClick,
        onStopDownload = { MapDownloadService.stopDownload(context) },
        onStartDownload = { region ->
            MapDownloadService.startDownload(
                context = context,
                region = region,
                layerName = layerId,
                minZoom = 5,
                maxZoom = 12
            )
        },
        onDeleteRequest = { region -> showDeleteDialog = region },
        onConfirmDelete = { region ->
            scope.launch {
                downloadManager.deleteRegionTiles(region, layerId)
                showDeleteDialog = null
                refreshTrigger++
            }
        },
        onDismissDelete = { showDeleteDialog = null },
        getRegionTileInfo = { region -> downloadManager.getRegionTileInfo(region, layerId) },
        refreshTrigger = refreshTrigger
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerRegionsScreenContent(
    layerDisplayName: String,
    layerId: String,
    regions: List<Region>,
    isDownloading: Boolean,
    downloadRegionName: String?,
    downloadLayerName: String?,
    downloadProgress: Int,
    showDeleteDialog: Region?,
    onBackClick: () -> Unit,
    onStopDownload: () -> Unit,
    onStartDownload: (Region) -> Unit,
    onDeleteRequest: (Region) -> Unit,
    onConfirmDelete: (Region) -> Unit,
    onDismissDelete: () -> Unit,
    getRegionTileInfo: suspend (Region) -> MapDownloadManager.RegionTileInfo?,
    refreshTrigger: Int
) {
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun cleanRegionName(name: String): String = name.substringBefore(" - ")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(layerDisplayName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Download Regions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Show downloading progress if active
            if (isDownloading && downloadRegionName != null && downloadLayerName == layerId) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Downloading ${cleanRegionName(downloadRegionName)}...")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$downloadProgress%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = onStopDownload) {
                                    Text("Stop")
                                }
                            }
                        }
                    }
                }
            }

            // List regions
            items(regions) { region ->
                @Suppress("UNUSED_VARIABLE")
                val trigger = refreshTrigger

                var tileInfo by remember { mutableStateOf<MapDownloadManager.RegionTileInfo?>(null) }

                LaunchedEffect(region, layerId) {
                    tileInfo = getRegionTileInfo(region)
                }

                val info = tileInfo
                val isDownloaded = info?.isFullyDownloaded == true
                val hasPartialDownload = (info?.downloadedTiles ?: 0) > 0

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text(cleanRegionName(region.name)) },
                        supportingContent = {
                            if (isDownloaded) {
                                Text("✓ ${info.downloadedTiles} tiles • ${formatBytes(info.downloadedSize)}")
                            } else if (hasPartialDownload) {
                                Text(
                                    "${info?.downloadedTiles ?: 0}/${info?.totalTiles ?: 0} tiles • ${
                                        formatBytes(
                                            info?.downloadedSize ?: 0
                                        )
                                    }"
                                )
                            } else {
                                Text("${info?.totalTiles ?: 0} tiles needed")
                            }
                        },
                        trailingContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (hasPartialDownload || isDownloaded) {
                                    IconButton(onClick = { onDeleteRequest(region) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                                Button(
                                    onClick = { onStartDownload(region) },
                                    enabled = !isDownloading
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

    // Delete confirmation dialog
    showDeleteDialog?.let { region ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Delete ${cleanRegionName(region.name)}?") },
            text = { Text("This will delete all $layerDisplayName tiles for ${cleanRegionName(region.name)}. You can re-download them later.") },
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(region) }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun DownloadScreenPreview() {
    val sampleLayers = listOf(
        LayerInfo("kartverket", "Kartverket", "Topographic maps from Kartverket"),
        LayerInfo("osm", "OpenStreetMap", "Community-sourced street maps"),
        LayerInfo("opentopomap", "OpenTopoMap", "Topographic maps with hiking trails")
    )
    DownloadScreenContent(
        layers = sampleLayers,
        cacheSize = 52_428_800L, // 50 MB
        isDownloading = false,
        downloadRegionName = null,
        downloadLayerName = null,
        downloadProgress = 0,
        onBackClick = {},
        onLayerClick = {},
        onStopDownload = {},
        getLayerStats = { 0L to 0 },
        refreshTrigger = 0
    )
}

@Preview(showSystemUi = true)
@Composable
private fun LayerRegionsScreenPreview() {
    val sampleRegions = listOf(
        Region("Oslo", LatLngBounds.from(60.0, 11.0, 59.7, 10.5)),
        Region("Bergen", LatLngBounds.from(60.5, 5.5, 60.2, 5.2)),
        Region("Trondheim", LatLngBounds.from(63.5, 10.6, 63.3, 10.2))
    )
    LayerRegionsScreenContent(
        layerDisplayName = "Kartverket",
        layerId = "kartverket",
        regions = sampleRegions,
        isDownloading = false,
        downloadRegionName = null,
        downloadLayerName = null,
        downloadProgress = 0,
        showDeleteDialog = null,
        onBackClick = {},
        onStopDownload = {},
        onStartDownload = {},
        onDeleteRequest = {},
        onConfirmDelete = {},
        onDismissDelete = {},
        getRegionTileInfo = { null },
        refreshTrigger = 0
    )
}
