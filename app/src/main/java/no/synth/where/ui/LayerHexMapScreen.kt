package no.synth.where.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import no.synth.where.data.DownloadLayers
import no.synth.where.data.HexGrid
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.RegionTileInfo
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.service.MapDownloadService
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private val NORWAY_BOUNDS = LatLngBounds(south = 56.0, west = 3.0, north = 72.0, east = 32.0)

@Composable
fun LayerHexMapScreen(
    layerId: String,
    onBackClick: () -> Unit,
    offlineModeEnabled: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val downloadState by MapDownloadService.downloadState.collectAsState()

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
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

    // Fetch downloaded hex IDs and reload the map style in one effect. Keying on
    // downloadingHexId means this re-runs both when a download starts (showing the
    // blue "in-progress" hex) and when it completes (downloadingHexId → null), so
    // the freshly-downloaded hex turns green immediately. refreshTrigger covers
    // explicit invalidations (e.g. after a delete).
    LaunchedEffect(mapInstance, downloadingHexId, refreshTrigger) {
        val map = mapInstance ?: return@LaunchedEffect
        val info = downloadManager.getDownloadedRegionsForLayer(layerId)
        downloadedHexIds = info.keys.toSet()
        val geoJson = buildHexGeoJson(allHexes, downloadedHexIds, downloadingHexId)
        map.setStyle(Style.Builder().fromJson(buildHexMapStyle(layerId, geoJson)))
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
        onStopDownload = { MapDownloadService.stopDownload(context) },
        onDownloadHex = {
            currentHex?.let { hex ->
                MapDownloadService.startDownload(
                    context = context,
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
        onDismissDelete = { showDeleteDialog = false },
        onDismissHex = {
            selectedHex = null
            selectedHexInfo = null
        },
        mapContent = {
            HexMapView(
                onMapReady = { map ->
                    mapInstance = map
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(65.0, 14.0))
                        .zoom(5.0)
                        .build()
                },
                onMapClick = { lat, lon ->
                    val hex = HexGrid.hexAtPoint(lat, lon)
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
            )
        }
    )
}

@Composable
private fun HexMapView(
    onMapReady: (MapLibreMap) -> Unit,
    onMapClick: (Double, Double) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mapView = it }.apply {
                onCreate(null)
                getMapAsync { map ->
                    onMapReady(map)
                    map.addOnMapClickListener { point ->
                        onMapClick(point.latitude, point.longitude)
                        true
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            mapView?.let {
                when (event) {
                    Lifecycle.Event.ON_START -> it.onStart()
                    Lifecycle.Event.ON_RESUME -> it.onResume()
                    Lifecycle.Event.ON_PAUSE -> it.onPause()
                    Lifecycle.Event.ON_STOP -> it.onStop()
                    Lifecycle.Event.ON_DESTROY -> it.onDestroy()
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }
}
