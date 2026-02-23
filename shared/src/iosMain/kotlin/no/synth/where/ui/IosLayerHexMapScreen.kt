package no.synth.where.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.synth.where.data.DownloadLayers
import no.synth.where.data.HexGrid
import no.synth.where.data.IosMapDownloadManager
import no.synth.where.data.RegionTileInfo
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.ui.map.MapClickCallback
import no.synth.where.ui.map.MapViewProvider

// Static bounds covering Norway (used to pre-generate the hex grid)
private val NORWAY_BOUNDS = LatLngBounds(south = 56.0, west = 3.0, north = 72.0, east = 32.0)

@Composable
fun IosLayerHexMapScreen(
    layerId: String,
    onBackClick: () -> Unit,
    hexMapViewProvider: MapViewProvider,
    downloadManager: IosMapDownloadManager,
    offlineModeEnabled: Boolean = false,
    onOfflineChipClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val downloadState by downloadManager.downloadState.collectAsState()

    var downloadedHexIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedHex by remember { mutableStateOf<HexGrid.Hex?>(null) }
    var selectedHexInfo by remember { mutableStateOf<RegionTileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val layerDisplayName = remember(layerId) {
        DownloadLayers.all.find { it.id == layerId }?.displayName ?: layerId
    }

    val downloadingHexId = if (downloadState.isDownloading && downloadState.layerName == layerId)
        downloadState.region?.name else null

    val allHexes = remember { HexGrid.hexesInBounds(NORWAY_BOUNDS) }

    // Fetch downloaded hex IDs, build GeoJSON, and apply style in one effect so
    // there is no race between data fetching and style reloading. Keying on
    // downloadingHexId re-runs both when a download starts AND when it completes
    // (downloadingHexId → null). When not actively downloading, retry with a short
    // delay because MLNOfflineStorage.packs loads asynchronously on the Swift side.
    LaunchedEffect(refreshTrigger, downloadingHexId) {
        downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        if (downloadingHexId == null) {
            // Packs may not be ready yet on first call or right after download completes
            delay(500)
            downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        }
        val hexGeoJson = buildHexGeoJson(allHexes, downloadedHexIds, downloadingHexId)
        hexMapViewProvider.setStyle(buildHexMapStyle(layerId, hexGeoJson))
    }

    DisposableEffect(Unit) {
        hexMapViewProvider.setOnMapClickCallback(object : MapClickCallback {
            override fun onMapClick(latitude: Double, longitude: Double) {
                val hex = HexGrid.hexAtPoint(latitude, longitude)
                if (selectedHex == hex) {
                    selectedHex = null
                    selectedHexInfo = null
                } else {
                    selectedHex = hex
                    selectedHexInfo = null
                    scope.launch {
                        selectedHexInfo = downloadManager.getRegionTileInfo(
                            HexGrid.hexToRegion(hex), layerId
                        )
                    }
                }
            }
        })
        onDispose {
            hexMapViewProvider.setOnMapClickCallback(null)
        }
    }

    val currentHex = selectedHex
    val hexTileInfo = selectedHexInfo
    val isHexDownloaded = hexTileInfo?.isFullyDownloaded == true
    val isHexPartial = (hexTileInfo?.downloadedTiles ?: 0) > 0

    HexMapScreenContent(
        layerDisplayName = layerDisplayName,
        isDownloading = downloadState.isDownloading,
        downloadLayerId = downloadState.layerName,
        currentLayerId = layerId,
        downloadProgress = downloadState.progress,
        selectedHexInfo = hexTileInfo,
        isHexSelected = currentHex != null,
        isHexDownloaded = isHexDownloaded,
        isHexPartiallyDownloaded = isHexPartial,
        offlineModeEnabled = offlineModeEnabled,
        showDeleteDialog = showDeleteDialog,
        onBackClick = onBackClick,
        onStopDownload = { downloadManager.stopDownload() },
        onDownloadHex = {
            currentHex?.let { hex ->
                downloadManager.startDownload(
                    region = HexGrid.hexToRegion(hex),
                    layerName = layerId,
                    minZoom = 5,
                    maxZoom = 12
                )
            }
            selectedHex = null
            selectedHexInfo = null
        },
        onDeleteHexRequest = { showDeleteDialog = true },
        onConfirmDelete = {
            currentHex?.let { hex ->
                scope.launch {
                    downloadManager.deleteRegionTiles(HexGrid.hexToRegion(hex), layerId)
                    showDeleteDialog = false
                    selectedHex = null
                    selectedHexInfo = null
                    refreshTrigger++
                }
            }
        },
        onZoomIn = { hexMapViewProvider.zoomIn() },
        onZoomOut = { hexMapViewProvider.zoomOut() },
        onOfflineChipClick = onOfflineChipClick,
        onDismissDelete = { showDeleteDialog = false },
        onDismissHex = {
            selectedHex = null
            selectedHexInfo = null
        },
        mapContent = {
            UIKitView(
                factory = { hexMapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
