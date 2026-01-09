package no.synth.where.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import no.synth.where.service.MapDownloadService
import java.io.File
import java.util.Locale

data class LayerInfo(
    val id: String,
    val displayName: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
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

    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    suspend fun getLayerStats(layerName: String): Pair<Long, Int> {
        return downloadManager.getLayerStats(layerName)
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
            if (downloadState.isDownloading && downloadState.region != null) {
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
                                        "Downloading ${downloadState.region?.name}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        downloadState.layerName ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    MapDownloadService.stopDownload(context)
                                }) {
                                    Text("Stop")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${downloadState.progress}%", style = MaterialTheme.typography.bodySmall)
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

@OptIn(ExperimentalMaterial3Api::class)
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
            if (downloadState.isDownloading && downloadState.region != null && downloadState.layerName == layerId) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Downloading ${cleanRegionName(downloadState.region?.name ?: "")}...")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${downloadState.progress}%", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = {
                                    MapDownloadService.stopDownload(context)
                                }) {
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
                    tileInfo = downloadManager.getRegionTileInfo(region, layerId)
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
                                Text("${info?.downloadedTiles ?: 0}/${info?.totalTiles ?: 0} tiles • ${formatBytes(info?.downloadedSize ?: 0)}")
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
                                    IconButton(onClick = { showDeleteDialog = region }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                    }
                                }
                                Button(
                                    onClick = {
                                        MapDownloadService.startDownload(
                                            context = context,
                                            region = region,
                                            layerName = layerId,
                                            minZoom = 5,
                                            maxZoom = 12
                                        )
                                    },
                                    enabled = !downloadState.isDownloading
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
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${cleanRegionName(region.name)}?") },
            text = { Text("This will delete all $layerDisplayName tiles for ${cleanRegionName(region.name)}. You can re-download them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadManager.deleteRegionTiles(region, layerId)
                            showDeleteDialog = null
                            refreshTrigger++
                        }
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

