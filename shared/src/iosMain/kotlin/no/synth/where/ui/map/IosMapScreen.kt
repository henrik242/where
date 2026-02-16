package no.synth.where.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import no.synth.where.data.GeocodingHelper
import no.synth.where.data.MapStyle
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.SavedPoint
import no.synth.where.data.SavedPointsRepository
import no.synth.where.data.TrackRepository
import no.synth.where.location.IosLocationTracker
import no.synth.where.util.NamingUtils
import org.koin.mp.KoinPlatform.getKoin

@OptIn(FlowPreview::class)
@Composable
fun IosMapScreen(
    mapViewProvider: MapViewProvider,
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    showWaymarkedTrails: Boolean = false,
    showCountyBorders: Boolean = false,
    viewingPoint: SavedPoint? = null,
    onClearViewingPoint: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val koin = remember { getKoin() }
    val trackRepository = remember { koin.get<TrackRepository>() }
    val savedPointsRepository = remember { koin.get<SavedPointsRepository>() }
    val locationTracker = remember { IosLocationTracker(trackRepository) }

    var showLayerMenu by remember { mutableStateOf(false) }
    var currentLayer by remember { mutableStateOf(selectedLayer) }
    var waymarkedTrails by remember { mutableStateOf(showWaymarkedTrails) }
    var countyBorders by remember { mutableStateOf(showCountyBorders) }
    var showSavedPoints by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val rulerState = remember { RulerState() }
    val scope = rememberCoroutineScope()

    val isRecording by trackRepository.isRecording.collectAsState()
    val currentTrack by trackRepository.currentTrack.collectAsState()
    val viewingTrack by trackRepository.viewingTrack.collectAsState()
    val savedPoints by savedPointsRepository.savedPoints.collectAsState()

    var showStopTrackDialog by remember { mutableStateOf(false) }
    var trackNameInput by remember { mutableStateOf("") }
    var isResolvingTrackName by remember { mutableStateOf(false) }

    // Search state
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<PlaceSearchClient.SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val styleJson = remember(currentLayer, waymarkedTrails, countyBorders) {
        MapStyle.getStyle(
            selectedLayer = currentLayer,
            showCountyBorders = countyBorders,
            showWaymarkedTrails = waymarkedTrails
        )
    }

    LaunchedEffect(Unit) {
        if (!locationTracker.hasPermission) {
            locationTracker.requestPermission()
        }
    }

    // Debounced search
    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300)
            .distinctUntilChanged()
            .collect { query ->
                if (query.length < 2) {
                    searchResults = emptyList()
                    isSearching = false
                    return@collect
                }
                isSearching = true
                searchResults = PlaceSearchClient.search(query)
                isSearching = false
            }
    }

    // Animate camera to viewing point
    LaunchedEffect(viewingPoint) {
        if (viewingPoint != null) {
            mapViewProvider.setCamera(
                latitude = viewingPoint.latLng.latitude,
                longitude = viewingPoint.latLng.longitude,
                zoom = 15.0
            )
        }
    }

    // Fit camera to viewing track bounds and render blue track line
    LaunchedEffect(viewingTrack) {
        val track = viewingTrack
        if (track != null && track.points.size >= 2 && !isRecording) {
            val geoJson = buildTrackGeoJson(track.points)
            mapViewProvider.updateTrackLine(geoJson, "#0000FF")
            val lats = track.points.map { it.latLng.latitude }
            val lngs = track.points.map { it.latLng.longitude }
            mapViewProvider.setCameraBounds(
                south = lats.min(),
                west = lngs.min(),
                north = lats.max(),
                east = lngs.max(),
                padding = 80
            )
        }
    }

    if (showStopTrackDialog) {
        MapDialogs.StopTrackDialog(
            trackNameInput = trackNameInput,
            onTrackNameChange = { trackNameInput = it },
            onDiscard = {
                trackRepository.discardRecording()
                locationTracker.stopTracking()
                mapViewProvider.clearTrackLine()
                showStopTrackDialog = false
                trackNameInput = ""
                scope.launch { snackbarHostState.showSnackbar("Track discarded") }
            },
            onSave = {
                val current = currentTrack
                val name = trackNameInput
                if (current != null && name.isNotBlank()) {
                    trackRepository.renameTrack(current, name)
                }
                trackRepository.stopRecording()
                locationTracker.stopTracking()
                mapViewProvider.clearTrackLine()
                showStopTrackDialog = false
                trackNameInput = ""
                scope.launch { snackbarHostState.showSnackbar("Track saved") }
            },
            onDismiss = {
                showStopTrackDialog = false
            },
            isLoading = isResolvingTrackName
        )
    }

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = isRecording,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = currentLayer,
        showWaymarkedTrails = waymarkedTrails,
        showCountyBorders = countyBorders,
        showSavedPoints = showSavedPoints,
        onlineTrackingEnabled = false,
        recordingDistance = currentTrack?.getDistanceMeters(),
        viewingTrackName = viewingTrack?.name,
        viewingPointName = viewingPoint?.name,
        viewingPointColor = viewingPoint?.color ?: "#FF5722",
        showViewingPoint = viewingPoint != null,
        showSearch = showSearch,
        searchQuery = searchQuery,
        searchResults = searchResults,
        isSearching = isSearching,
        onSearchClick = { showSearch = true },
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { currentLayer = it },
        onWaymarkedTrailsToggle = { waymarkedTrails = !waymarkedTrails },
        onCountyBordersToggle = { countyBorders = !countyBorders },
        onSavedPointsToggle = { showSavedPoints = !showSavedPoints },
        onRecordStopClick = {
            if (isRecording) {
                showStopTrackDialog = true
                trackNameInput = ""
                isResolvingTrackName = true
                scope.launch {
                    val track = currentTrack
                    if (track != null && track.points.isNotEmpty()) {
                        val firstPoint = track.points.first()
                        val lastPoint = track.points.last()
                        val startName = GeocodingHelper.reverseGeocode(firstPoint.latLng)
                        val distance = firstPoint.latLng.distanceTo(lastPoint.latLng)
                        val baseName = if (distance > 100 && startName != null) {
                            val endName = GeocodingHelper.reverseGeocode(lastPoint.latLng)
                            if (endName != null && startName != endName) {
                                "$startName → $endName"
                            } else {
                                startName
                            }
                        } else {
                            startName
                        }
                        if (baseName != null) {
                            trackNameInput = NamingUtils.makeUnique(
                                baseName,
                                trackRepository.tracks.value.map { it.name }
                            )
                        }
                    }
                    isResolvingTrackName = false
                }
            } else {
                if (!locationTracker.hasPermission) {
                    locationTracker.requestPermission()
                    scope.launch { snackbarHostState.showSnackbar("Location permission required") }
                    return@MapScreenContent
                }
                trackRepository.startNewTrack()
                locationTracker.startTracking()
                scope.launch { snackbarHostState.showSnackbar("Recording started") }
            }
        },
        onMyLocationClick = {
            val location = mapViewProvider.getUserLocation()
            if (location != null && location.size >= 2) {
                mapViewProvider.setCamera(
                    latitude = location[0],
                    longitude = location[1],
                    zoom = 15.0
                )
            }
        },
        onRulerToggle = {},
        onSettingsClick = onSettingsClick,
        onZoomIn = { mapViewProvider.zoomIn() },
        onZoomOut = { mapViewProvider.zoomOut() },
        onRulerUndo = {},
        onRulerClear = {},
        onRulerSaveAsTrack = {},
        onOnlineTrackingChange = {},
        onCloseViewingTrack = {
            trackRepository.clearViewingTrack()
            mapViewProvider.clearTrackLine()
        },
        onCloseViewingPoint = { onClearViewingPoint() },
        onSearchQueryChange = { searchQuery = it },
        onSearchResultClick = { result ->
            mapViewProvider.setCamera(
                latitude = result.latLng.latitude,
                longitude = result.latLng.longitude,
                zoom = 14.0
            )
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        onSearchClose = {
            showSearch = false
            searchQuery = ""
            searchResults = emptyList()
        },
        mapContent = {
            UIKitView(
                factory = { mapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize(),
                update = {
                    mapViewProvider.setStyle(styleJson)
                    mapViewProvider.setShowsUserLocation(true)

                    // Track rendering: recording (red) takes precedence over viewing (blue)
                    val recording = currentTrack
                    if (recording != null && recording.points.size >= 2) {
                        val geoJson = buildTrackGeoJson(recording.points)
                        mapViewProvider.updateTrackLine(geoJson, "#FF0000")
                    } else if (!isRecording) {
                        val viewing = viewingTrack
                        if (viewing != null && viewing.points.size >= 2) {
                            val geoJson = buildTrackGeoJson(viewing.points)
                            mapViewProvider.updateTrackLine(geoJson, "#0000FF")
                        } else {
                            mapViewProvider.clearTrackLine()
                        }
                    }

                    // Saved points rendering
                    if (showSavedPoints && savedPoints.isNotEmpty()) {
                        val geoJson = buildSavedPointsGeoJson(savedPoints)
                        mapViewProvider.updateSavedPoints(geoJson)
                    } else {
                        mapViewProvider.clearSavedPoints()
                    }
                }
            )
        }
    )
}
