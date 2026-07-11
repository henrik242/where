package no.synth.where.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import no.synth.where.data.MapDownloadManager
import no.synth.where.data.OfflineTileReader
import no.synth.where.data.QueuedDownload
import no.synth.where.data.RegionTileInfo
import no.synth.where.data.downloadingHexIds
import no.synth.where.data.forHex
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLngBounds
import no.synth.where.data.summary
import no.synth.where.ui.map.MapRenderUtils
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory

private val NORWAY_BOUNDS = LatLngBounds(south = 56.0, west = 3.0, north = 72.0, east = 32.0)

@Composable
fun LayerHexMapScreen(
    layerId: String,
    onBackClick: () -> Unit,
    onOfflineChipClick: () -> Unit = {},
    onQueueChipClick: () -> Unit = {},
    offlineModeEnabled: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val downloadManager = remember { MapDownloadManager(context) }
    val app = context.applicationContext as no.synth.where.WhereApplication
    val queue by app.downloadQueueManager.queue.collectAsState()
    val downloadElevationData by app.userPreferences.downloadElevationData.collectAsState()
    val downloadMaxZoom by app.userPreferences.downloadMaxZoom.collectAsState()
    val effectiveMaxZoom = DownloadLayers.effectiveMaxZoom(layerId, downloadMaxZoom)

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
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

    // Hexes queued or actively downloading for this layer (highlighted "in progress" on the map).
    val downloadingIds = queue.downloadingHexIds(layerId)

    val allHexes = remember { HexGrid.hexesInBounds(NORWAY_BOUNDS) }

    // Fetch downloaded hex IDs and reload the map style in one effect. Keying on
    // downloadingIds means this re-runs when a download starts (showing the blue
    // "in-progress" hex) and when it completes (leaves the set), so the freshly
    // downloaded hex turns green immediately. refreshTrigger covers explicit
    // invalidations (e.g. after a delete).
    LaunchedEffect(mapInstance, downloadingIds, refreshTrigger) {
        val map = mapInstance ?: return@LaunchedEffect
        val info = downloadManager.getDownloadedRegionsForLayer(layerId)
        downloadedHexIds = info.keys.toSet()
        val geoJson = buildHexGeoJson(allHexes, downloadedHexIds, downloadingIds)
        map.setStyle(Style.Builder().fromJson(buildHexMapStyle(layerId, geoJson))) { style ->
            MapRenderUtils.enableLocationComponent(map, style, context, hasLocationPermission)
        }
    }

    // Pulse the "downloading" hex fill so in-progress areas stand out. Gated to STARTED so the
    // 80ms tick loop does not keep waking the CPU for the whole download while the app is
    // backgrounded. Reads the layer fresh each tick so it survives style reloads.
    LaunchedEffect(mapInstance, downloadingIds.isEmpty()) {
        val map = mapInstance ?: return@LaunchedEffect
        if (downloadingIds.isEmpty()) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var phase = 0.0
            while (isActive) {
                val opacity = (0.35 + 0.2 * sin(phase)).toFloat()
                map.style?.getLayer("hex-downloading-fill")?.setProperties(PropertyFactory.fillOpacity(opacity))
                phase += 0.35
                delay(80)
            }
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
        onCancelHexDownload = { selectedHexDownload?.let { app.downloadQueueManager.cancel(it.id) } },
        onDownloadHex = {
            currentHex?.let { hex ->
                val coord = CoordinateFormatter.formatLatLng(HexGrid.hexCenter(hex))
                app.downloadQueueManager.enqueue(
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
        onZoomIn = { mapInstance?.animateCamera(CameraUpdateFactory.zoomIn()) },
        onZoomOut = { mapInstance?.animateCamera(CameraUpdateFactory.zoomOut()) },
        onOfflineChipClick = onOfflineChipClick,
        onQueueChipClick = onQueueChipClick,
        onDismissDelete = { showDeleteDialog = false },
        onDismissHex = {
            selectedHex = null
            selectedHexInfo = null
            selectedHexName = null
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
                    // Keep the compass on screen even when facing north (matches the main map).
                    map.uiSettings.setCompassFadeFacingNorth(false)
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
