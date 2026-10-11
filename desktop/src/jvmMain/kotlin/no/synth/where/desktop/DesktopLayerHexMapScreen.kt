package no.synth.where.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.synth.where.data.DownloadLayers
import no.synth.where.data.DownloadStatus
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.HexGrid
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.QueuedDownload
import no.synth.where.data.RegionTileInfo
import no.synth.where.data.UserPreferences
import no.synth.where.data.downloadingHexIds
import no.synth.where.data.forHex
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.summary
import no.synth.where.ui.HexMapScreenContent
import no.synth.where.ui.buildHexGeoJson
import no.synth.where.ui.map.compose.HexComposeMap
import no.synth.where.ui.map.compose.WhereMapController
import kotlin.math.sin

private val NORWAY_BOUNDS = LatLngBounds(south = 56.0, west = 3.0, north = 72.0, east = 32.0)

@Composable
fun DesktopLayerHexMapScreen(
    layerId: String,
    onBackClick: () -> Unit,
    downloadManager: no.synth.where.data.DesktopMapDownloadManager,
    downloadElevationData: Boolean = true,
    downloadMaxZoom: Int = UserPreferences.DEFAULT_DOWNLOAD_MAX_ZOOM,
    offlineModeEnabled: Boolean = false,
    onOfflineChipClick: () -> Unit = {},
    onQueueChipClick: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val queue by downloadManager.queue.collectAsState()
    val controller = remember { WhereMapController() }
    var downloadingOpacity by remember { mutableStateOf(0.4f) }

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

    LaunchedEffect(refreshTrigger, downloadingIds) {
        downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        if (downloadingIds.isEmpty()) {
            delay(500)
            downloadedHexIds = downloadManager.getDownloadedRegionsForLayer(layerId)
        }
    }

    val hexGeoJson = remember(downloadedHexIds, downloadingIds) {
        buildHexGeoJson(allHexes, downloadedHexIds, downloadingIds)
    }

    LaunchedEffect(downloadingIds.isEmpty()) {
        if (downloadingIds.isEmpty()) {
            downloadingOpacity = 0.4f
            return@LaunchedEffect
        }
        var phase = 0.0
        while (isActive) {
            downloadingOpacity = (0.35 + 0.2 * sin(phase)).toFloat()
            phase += 0.35
            delay(80)
        }
    }

    val onHexTap: (LatLng) -> Unit = { latLng ->
        val hex = HexGrid.hexAtPoint(latLng.latitude, latLng.longitude)
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
                selectedHexInfo = downloadManager.getRegionTileInfo(HexGrid.hexToRegion(hex), layerId, maxZoom = effectiveMaxZoom)
            }
            scope.launch {
                selectedHexName = GeocodingHelper.reverseGeocodeArea(HexGrid.hexCenter(hex))
                isLoadingHexName = false
            }
        }
    }

    val currentHex = selectedHex
    val hexTileInfo = selectedHexInfo
    val isHexDownloaded = hexTileInfo?.isFullyDownloaded == true
    val isHexPartial = (hexTileInfo?.downloadedTiles ?: 0) > 0
    val selectedHexDownload = currentHex?.let { hex -> queue.forHex(hex.id, layerId) }
    val queueSummary = queue.summary()

    LaunchedEffect(selectedHexDownload?.status) {
        val hex = currentHex ?: return@LaunchedEffect
        if (selectedHexDownload?.status == DownloadStatus.COMPLETED) {
            selectedHexInfo = downloadManager.getRegionTileInfo(HexGrid.hexToRegion(hex), layerId, maxZoom = effectiveMaxZoom)
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
                    if (!hasOther) OfflineTileReader.deleteDemTilesForBounds(region.boundingBox)
                    showDeleteDialog = false
                    selectedHex = null
                    selectedHexInfo = null
                    selectedHexName = null
                    refreshTrigger++
                }
            }
        },
        onZoomIn = { controller.zoomIn() },
        onZoomOut = { controller.zoomOut() },
        onOfflineChipClick = onOfflineChipClick,
        onQueueChipClick = onQueueChipClick,
        onDismissDelete = { showDeleteDialog = false },
        onDismissHex = {
            selectedHex = null
            selectedHexInfo = null
            selectedHexName = null
        },
        mapContent = {
            HexComposeMap(
                layerId = layerId,
                hexGeoJson = hexGeoJson,
                downloadingOpacity = downloadingOpacity,
                modifier = Modifier.fillMaxSize(),
                controller = controller,
                userLocation = null,
                onMapClick = onHexTap,
            )
        },
    )
}
