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
    SJOKARTRASTER,
    OSM,
    OPENTOPOMAP,
    WAYMARKEDTRAILS
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
    var cacheSize by remember { mutableStateOf(0L) }

    // Calculate cache size on screen load and when refresh is triggered
    LaunchedEffect(refreshTrigger) {
        // Check MapLibre's tile storage directory (external files, not cache)
        val maplibreTilesDir = File(context.getExternalFilesDir(null), "maplibre-tiles")
        cacheSize = if (maplibreTilesDir.exists()) {
            maplibreTilesDir.walkTopDown().sumOf { file ->
                if (file.isFile) file.length() else 0L
            }
        } else {
            // Fallback to cache directory if the new path doesn't exist yet
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

    fun cleanRegionName(name: String): String = name.substringBefore(" - ")

    suspend fun getLayerStats(layerName: String): Pair<Long, Int> {
        // Query MapLibre's OfflineManager for stats about this layer
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
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("kartverket")
                    }
                    LayerOverviewCard(
                        layerName = "Kartverket",
                        description = "Kartverket topographic maps",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.KARTVERKET,
                        onClick = { selectedTab = DownloadTab.KARTVERKET }
                    )
                }

                // Toporaster layer card
                item {
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("toporaster")
                    }
                    LayerOverviewCard(
                        layerName = "Kartverket toporaster",
                        description = "Kartverket topographic raster maps",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.TOPORASTER,
                        onClick = { selectedTab = DownloadTab.TOPORASTER }
                    )
                }

                // Sjøkartraster layer card
                item {
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("sjokartraster")
                    }
                    LayerOverviewCard(
                        layerName = "Kartverket sjøkart",
                        description = "Kartverket nautical charts",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.SJOKARTRASTER,
                        onClick = { selectedTab = DownloadTab.SJOKARTRASTER }
                    )
                }

                // OSM layer card
                item {
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("osm")
                    }
                    LayerOverviewCard(
                        layerName = "OpenStreetMap",
                        description = "Community-sourced street maps",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.OSM,
                        onClick = { selectedTab = DownloadTab.OSM }
                    )
                }

                // OpenTopoMap layer card
                item {
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("opentopomap")
                    }
                    LayerOverviewCard(
                        layerName = "OpenTopoMap",
                        description = "Topographic maps with hiking trails (OSM)",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.OPENTOPOMAP,
                        onClick = { selectedTab = DownloadTab.OPENTOPOMAP }
                    )
                }

                // Waymarked Trails layer card
                item {
                    var stats by remember { mutableStateOf(Pair(0L, 0)) }
                    LaunchedEffect(refreshTrigger) {
                        stats = getLayerStats("waymarkedtrails")
                    }
                    LayerOverviewCard(
                        layerName = "Waymarked Trails",
                        description = "Hiking trail overlay (OSM-based)",
                        tileCount = stats.second,
                        totalSize = stats.first,
                        formatBytes = ::formatBytes,
                        isSelected = selectedTab == DownloadTab.WAYMARKEDTRAILS,
                        onClick = { selectedTab = DownloadTab.WAYMARKEDTRAILS }
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
                            DownloadTab.SJOKARTRASTER -> "Sjøkartraster Regions"
                            DownloadTab.OSM -> "OSM Regions"
                            DownloadTab.OPENTOPOMAP -> "OpenTopoMap Regions"
                            DownloadTab.WAYMARKEDTRAILS -> "Waymarked Trails Regions"
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
                                Text("Downloading ${cleanRegionName(downloadingRegion?.name ?: "")}...")
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
                        DownloadTab.SJOKARTRASTER -> "sjokartraster"
                        DownloadTab.OSM -> "osm"
                        DownloadTab.OPENTOPOMAP -> "opentopomap"
                        DownloadTab.WAYMARKEDTRAILS -> "waymarkedtrails"
                    }

                    var tileInfo by remember { mutableStateOf<MapDownloadManager.RegionTileInfo?>(null) }

                    LaunchedEffect(region, layerName) {
                        tileInfo = downloadManager.getRegionTileInfo(region, layerName)
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
            DownloadTab.SJOKARTRASTER -> "sjokartraster"
            DownloadTab.OSM -> "osm"
            DownloadTab.OPENTOPOMAP -> "opentopomap"
            DownloadTab.WAYMARKEDTRAILS -> "waymarkedtrails"
        }

        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete ${cleanRegionName(region.name)}?") },
            text = { Text("This will delete all $layerName tiles for ${cleanRegionName(region.name)}. You can re-download them later.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloadManager.deleteRegionTiles(region, layerName)
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
