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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import no.synth.where.data.DownloadLayers
import no.synth.where.data.DownloadStatus
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.HexGrid
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.IosMapDownloadManager
import no.synth.where.data.QueuedDownload
import no.synth.where.data.RegionTileInfo
import no.synth.where.data.UserPreferences
import no.synth.where.data.downloadingHexIds
import no.synth.where.data.forHex
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.summary
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
    downloadElevationData: Boolean = true,
    downloadMaxZoom: Int = UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM,
    offlineModeEnabled: Boolean = false,
    onOfflineChipClick: () -> Unit = {},
    onQueueChipClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val queue by downloadManager.queue.collectAsState()

    val effectiveMaxZoom = DownloadLayers.effectiveMaxZoom(layerId, downloadMaxZoom)

    var downloadedHexIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedHex by remember { mutableStateOf<HexGrid.Hex?>(null) }
    var selectedHexInfo by remember { mutableStateOf<RegionTileInfo?>(null) }
    var selectedHexName by remember { mutableStateOf<String?>(null) }
    var isLoadingHexName by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val layerDisplayName = remember(layerId) {
        DownloadLayers.all.find { it.id == layerId }?.displayName ?: layerId
    }

    val downloadingIds = queue.downloadingHexIds(layerId)

    val allHexes = remember { HexGrid.hexesInBounds(NORWAY_BOUNDS) }

    // Fetch downloaded hex IDs, build GeoJSON, and apply style in one effect so
    // there is no race between data fetching and style reloading. Keying on
    // downloadingHexId re-runs both when a download starts AND when it completes
    // (downloadingHexId → null). When not actively downloading, retry with a short
    // delay because MLNOfflineStorage.packs loads asynchronously on the Swift side.
    LaunchedEffect(refreshTrigger, downloadingIds) {
        downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        if (downloadingIds.isEmpty()) {
            // Packs may not be ready yet on first call or right after download completes
            delay(500)
            downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        }
        val hexGeoJson = buildHexGeoJson(allHexes, downloadedHexIds, downloadingIds)
        hexMapViewProvider.setStyle(buildHexMapStyle(layerId, hexGeoJson))
        hexMapViewProvider.setShowsUserLocation(true)
    }

    // Pulse the "downloading" hex fill while something is in progress. Gated to STARTED so the
    // 80ms tick loop does not keep running while the app is backgrounded (the location background
    // mode keeps the process scheduled during a recording).
    LaunchedEffect(downloadingIds.isEmpty()) {
        if (downloadingIds.isEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var phase = 0.0
            while (isActive) {
                hexMapViewProvider.setHexDownloadingOpacity(0.35 + 0.2 * sin(phase))
                phase += 0.35
                delay(80)
            }
        }
    }

    DisposableEffect(Unit) {
        hexMapViewProvider.setOnMapClickCallback(object : MapClickCallback {
            override fun onMapClick(latitude: Double, longitude: Double) {
                val hex = HexGrid.hexAtPoint(latitude, longitude)
                if (selectedHex == hex) {
                    selectedHex = null
                    selectedHexInfo = null
                    selectedHexName = null
                    isLoadingHexName = false
                } else {
                    selectedHex = hex
                    selectedHexInfo = null
                    selectedHexName = null
                    isLoadingHexName = true
                    scope.launch {
                        selectedHexInfo = downloadManager.getRegionTileInfo(
                            HexGrid.hexToRegion(hex), layerId, maxZoom = effectiveMaxZoom
                        )
                    }
                    scope.launch {
                        val center = HexGrid.hexCenter(hex)
                        selectedHexName = GeocodingHelper.reverseGeocodeArea(center)
                        isLoadingHexName = false
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
    val selectedHexDownload = currentHex?.let { hex -> queue.forHex(hex.id, layerId) }
    val queueSummary = queue.summary()

    // Refresh the selected hex's tile info once its download finishes, so the panel flips from
    // "downloading" to the downloaded size + Delete instead of a stale "Not downloaded".
    LaunchedEffect(selectedHexDownload?.status) {
        if (selectedHexDownload?.status == DownloadStatus.COMPLETED && currentHex != null) {
            selectedHexInfo = downloadManager.getRegionTileInfo(
                HexGrid.hexToRegion(currentHex), layerId, maxZoom = effectiveMaxZoom
            )
        }
    }

    HexMapScreenContent(
        layerDisplayName = layerDisplayName,
        selectedHexDownload = selectedHexDownload,
        queueSummary = queueSummary,
        selectedHexInfo = hexTileInfo,
        selectedHexName = selectedHexName,
        isLoadingHexName = isLoadingHexName,
        isHexSelected = currentHex != null,
        isHexDownloaded = isHexDownloaded,
        isHexPartiallyDownloaded = isHexPartial,
        offlineModeEnabled = offlineModeEnabled,
        showDeleteDialog = showDeleteDialog,
        onBackClick = onBackClick,
        onCancelHexDownload = { selectedHexDownload?.let { downloadManager.cancel(it.id) } },
        onDownloadHex = {
            currentHex?.let { hex ->
                val coord = CoordinateFormatter.formatLatLng(HexGrid.hexCenter(hex))
                downloadManager.enqueue(
                    QueuedDownload(
                        region = HexGrid.hexToRegion(hex),
                        layerId = layerId,
                        layerDisplayName = layerDisplayName,
                        label = selectedHexName?.let { "$it ($coord)" } ?: coord,
                        maxZoom = effectiveMaxZoom,
                        downloadDem = downloadElevationData,
                    )
                )
            }
        },
        onDeleteHexRequest = { showDeleteDialog = true },
        onConfirmDelete = {
            currentHex?.let { hex ->
                scope.launch {
                    val region = HexGrid.hexToRegion(hex)
                    val hasOther = downloadManager.hasOtherLayersForRegion(hex.id, layerId)
                    downloadManager.deleteRegionTiles(region, layerId)
                    if (!hasOther) {
                        OfflineTileReader.deleteDemTilesForBounds(region.boundingBox)
                    }
                    showDeleteDialog = false
                    selectedHex = null
                    selectedHexInfo = null
                    selectedHexName = null
                    refreshTrigger++
                }
            }
        },
        onZoomIn = { hexMapViewProvider.zoomIn() },
        onZoomOut = { hexMapViewProvider.zoomOut() },
        onOfflineChipClick = onOfflineChipClick,
        onQueueChipClick = onQueueChipClick,
        onDismissDelete = { showDeleteDialog = false },
        onDismissHex = {
            selectedHex = null
            selectedHexInfo = null
            selectedHexName = null
        },
        mapContent = {
            UIKitView(
                factory = { hexMapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
