package no.synth.where.ui.map

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import no.synth.where.data.MapStyle
import no.synth.where.data.PlatformFile
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RegionsRepository
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPointUtils
import no.synth.where.data.Track
import android.graphics.PointF
import android.view.MotionEvent
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.toCommon
import no.synth.where.data.geo.toMapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import no.synth.where.util.Logger

@Composable
fun MapLibreMapView(
    onMapReady: (MapLibreMap) -> Unit = {},
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    hasLocationPermission: Boolean = false,
    showCountyBorders: Boolean = true,
    showWaymarkedTrails: Boolean = false,
    showAvalancheZones: Boolean = false,
    showHillshade: Boolean = false,
    showSavedPoints: Boolean = true,
    savedPoints: List<no.synth.where.data.SavedPoint> = emptyList(),
    currentTrack: Track? = null,
    viewingTrack: Track? = null,
    savedCameraLat: Double = 65.0,
    savedCameraLon: Double = 10.0,
    savedCameraZoom: Double = 5.0,
    rulerState: RulerState = RulerState(),
    searchResults: List<PlaceSearchClient.SearchResult> = emptyList(),
    highlightedSearchResult: PlaceSearchClient.SearchResult? = null,
    friendTrackGeoJson: String? = null,
    regionsLoadedTrigger: Int = 0,
    onRulerPointAdded: (LatLng) -> Unit = {},
    onLongPress: (LatLng) -> Unit = {},
    onPointClick: (no.synth.where.data.SavedPoint) -> Unit = {},
    onTwoFingerMeasure: (TwoFingerMeasurement?) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val context = LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    var wasInitialized by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
            }

            override fun onLost(network: Network) {
                val activeNetwork = connectivityManager.activeNetwork
                isOnline = activeNetwork != null
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        isOnline = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    // Track click listeners to replace them when ruler state changes
    var clickListener by remember { mutableStateOf<MapLibreMap.OnMapClickListener?>(null) }
    var longClickListener by remember { mutableStateOf<MapLibreMap.OnMapLongClickListener?>(null) }

    LaunchedEffect(
        selectedLayer,
        showCountyBorders,
        showWaymarkedTrails,
        showAvalancheZones,
        showHillshade,
        showSavedPoints,
        savedPoints.size,
        isOnline,
        regionsLoadedTrigger,
        map
    ) {
        map?.let { mapInstance ->
            try {
                val regions = RegionsRepository.getRegions(PlatformFile(context.cacheDir))
                val styleJson = MapStyle.getStyle(
                    selectedLayer,
                    showCountyBorders,
                    showWaymarkedTrails,
                    showAvalancheZones,
                    showHillshade = showHillshade,
                    regions = regions
                )
                val viewing = viewingTrack
                val current = currentTrack
                mapInstance.setStyle(
                    Style.Builder().fromJson(styleJson),
                    object : Style.OnStyleLoaded {
                        override fun onStyleLoaded(style: Style) {
                            MapRenderUtils.enableLocationComponent(
                                mapInstance,
                                style,
                                context,
                                hasLocationPermission
                            )
                            val trackToShow = current ?: viewing
                            MapRenderUtils.updateTrackOnMap(
                                style,
                                trackToShow,
                                isCurrentTrack = current != null
                            )
                            MapRenderUtils.updateRulerOnMap(style, rulerState)
                            MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJson)

                            if (showSavedPoints && savedPoints.isNotEmpty()) {
                                MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
                            }

                            if (viewing == null && current == null) {
                                mapInstance.cameraPosition = CameraPosition.Builder()
                                    .target(LatLng(savedCameraLat, savedCameraLon).toMapLibre())
                                    .zoom(savedCameraZoom)
                                    .build()
                            }
                        }
                    })
            } catch (e: Exception) {
                Logger.e(e, "Map screen error")
            }
        }
    }

    LaunchedEffect(hasLocationPermission, map) {
        val mapInstance = map
        if (hasLocationPermission && mapInstance != null) {
            mapInstance.style?.let { style ->
                MapRenderUtils.enableLocationComponent(
                    mapInstance,
                    style,
                    context,
                    hasLocationPermission
                )
            }
        }
    }

    LaunchedEffect(rulerState, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateRulerOnMap(style, rulerState)
        }
    }

    LaunchedEffect(isOnline, map) {
        if (wasInitialized && isOnline && map != null) {

            map?.let { mapInstance ->
                val regions = RegionsRepository.getRegions(PlatformFile(context.cacheDir))
                val styleJson = MapStyle.getStyle(
                    selectedLayer,
                    showCountyBorders,
                    showWaymarkedTrails,
                    showAvalancheZones,
                    showHillshade = showHillshade,
                    regions = regions
                )
                val viewing = viewingTrack
                val current = currentTrack

                try {
                    mapInstance.setStyle(
                        Style.Builder().fromJson(styleJson),
                        object : Style.OnStyleLoaded {
                            override fun onStyleLoaded(style: Style) {
                                MapRenderUtils.enableLocationComponent(
                                    mapInstance,
                                    style,
                                    context,
                                    hasLocationPermission
                                )
                                val trackToShow = current ?: viewing
                                MapRenderUtils.updateTrackOnMap(
                                    style,
                                    trackToShow,
                                    isCurrentTrack = current != null
                                )
                                MapRenderUtils.updateRulerOnMap(style, rulerState)
                                MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJson)

                                if (showSavedPoints && savedPoints.isNotEmpty()) {
                                    MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
                                }
                            }
                        })
                } catch (e: Exception) {
                    Logger.e(e, "Map screen error")
                }
            }
        }

        if (map != null) {
            wasInitialized = true
        }
    }

    // Update saved points on map when they change (including color changes)
    LaunchedEffect(savedPoints.toList(), showSavedPoints) {
        map?.getStyle { style ->
            if (showSavedPoints) {
                MapRenderUtils.updateSavedPointsOnMap(style, savedPoints)
            } else {
                // Remove saved points layer when hidden
                try {
                    style.getLayer("saved-points-layer")?.let { style.removeLayer(it) }
                    style.getSource("saved-points-source")?.let { style.removeSource(it) }
                } catch (e: Exception) {
                    Logger.e(e, "Map screen error")
                }
            }
        }
    }

    LaunchedEffect(friendTrackGeoJson, map) {
        map?.style?.let { style ->
            MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJson)
        }
    }

    LaunchedEffect(searchResults, map) {
        map?.getStyle { style ->
            MapRenderUtils.updateSearchResultsOnMap(style, searchResults)
        }
    }

    LaunchedEffect(highlightedSearchResult, map) {
        map?.getStyle { style ->
            MapRenderUtils.updateHighlightedSearchResult(style, highlightedSearchResult)
        }
    }

    LaunchedEffect(rulerState.isActive, savedPoints.size, map) {
        map?.let { mapInstance ->
            // Remove old listeners if they exist
            clickListener?.let { mapInstance.removeOnMapClickListener(it) }
            longClickListener?.let { mapInstance.removeOnMapLongClickListener(it) }

            // Create and add new click listener
            val newClickListener = MapLibreMap.OnMapClickListener { point ->
                val commonPoint = point.toCommon()
                if (rulerState.isActive) {
                    onRulerPointAdded(commonPoint)
                    true
                } else {
                    val clickedSavedPoint = SavedPointUtils.findNearestPoint(commonPoint, savedPoints)

                    if (clickedSavedPoint != null) {
                        onPointClick(clickedSavedPoint)
                        true
                    } else {
                        false
                    }
                }
            }
            mapInstance.addOnMapClickListener(newClickListener)
            clickListener = newClickListener

            // Create and add new long click listener
            val newLongClickListener = MapLibreMap.OnMapLongClickListener { point ->
                if (!rulerState.isActive) {
                    onLongPress(point.toCommon())
                    true
                } else {
                    false
                }
            }
            mapInstance.addOnMapLongClickListener(newLongClickListener)
            longClickListener = newLongClickListener
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mapView = it }.apply {
                setOnTouchListener { v, event ->
                    val mapRef = map
                    val action = event.actionMasked
                    if (mapRef != null && event.pointerCount >= 2 &&
                        action != MotionEvent.ACTION_POINTER_UP
                    ) {
                        try {
                            val x1 = event.getX(0)
                            val y1 = event.getY(0)
                            val x2 = event.getX(1)
                            val y2 = event.getY(1)
                            val screenDist = kotlin.math.hypot(
                                (x2 - x1).toDouble(), (y2 - y1).toDouble()
                            )
                            val minPx = 48 * v.resources.displayMetrics.density
                            if (screenDist < minPx) {
                                onTwoFingerMeasure(null)
                            } else {
                                val proj = mapRef.projection
                                val ll1 = proj.fromScreenLocation(PointF(x1, y1)).toCommon()
                                val ll2 = proj.fromScreenLocation(PointF(x2, y2)).toCommon()
                                onTwoFingerMeasure(
                                    TwoFingerMeasurement(x1, y1, x2, y2, ll1.distanceTo(ll2))
                                )
                            }
                        } catch (e: Exception) {
                            Logger.d("Two-finger projection not ready: %s", e.message ?: "")
                        }
                    } else if (action == MotionEvent.ACTION_UP ||
                        action == MotionEvent.ACTION_CANCEL ||
                        action == MotionEvent.ACTION_POINTER_UP
                    ) {
                        onTwoFingerMeasure(null)
                    }
                    false
                }
                onCreate(null)
                getMapAsync { mapInstance ->
                    map = mapInstance
                    onMapReady(mapInstance)

                    mapInstance.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(savedCameraLat, savedCameraLon).toMapLibre())
                        .zoom(savedCameraZoom)
                        .build()

                    // Click listeners are handled by LaunchedEffect above
                    // Don't add any click listeners here to avoid conflicts

                    try {
                        val regions = RegionsRepository.getRegions(PlatformFile(ctx.cacheDir))
                        val styleJson = MapStyle.getStyle(
                            selectedLayer,
                            showCountyBorders,
                            showWaymarkedTrails,
                            showAvalancheZones,
                            showHillshade = showHillshade,
                            regions = regions
                        )
                        val viewing = viewingTrack
                        val current = currentTrack
                        mapInstance.setStyle(
                            Style.Builder().fromJson(styleJson),
                            object : Style.OnStyleLoaded {
                                override fun onStyleLoaded(style: Style) {
                                    MapRenderUtils.enableLocationComponent(
                                        mapInstance,
                                        style,
                                        ctx,
                                        hasLocationPermission
                                    )
                                    val trackToShow = current ?: viewing
                                    MapRenderUtils.updateTrackOnMap(
                                        style,
                                        trackToShow,
                                        isCurrentTrack = current != null
                                    )
                                    MapRenderUtils.updateRulerOnMap(style, rulerState)
                                    MapRenderUtils.updateFriendTrackOnMap(style, friendTrackGeoJson)
                                    mapInstance.triggerRepaint()
                                }
                            })
                    } catch (e: Exception) {
                        Logger.e(e, "Map screen error")
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
