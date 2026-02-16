package no.synth.where.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import no.synth.where.data.MapStyle
import no.synth.where.data.RulerState

@Composable
fun IosMapScreen(
    mapViewProvider: MapViewProvider,
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    showWaymarkedTrails: Boolean = false,
    showCountyBorders: Boolean = false,
    onSettingsClick: () -> Unit = {},
    onMyLocationClick: () -> Unit = {}
) {
    var showLayerMenu by remember { mutableStateOf(false) }
    var currentLayer by remember { mutableStateOf(selectedLayer) }
    var waymarkedTrails by remember { mutableStateOf(showWaymarkedTrails) }
    var countyBorders by remember { mutableStateOf(showCountyBorders) }
    var showSavedPoints by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val rulerState = remember { RulerState() }

    LaunchedEffect(currentLayer, waymarkedTrails, countyBorders) {
        val styleJson = MapStyle.getStyle(
            selectedLayer = currentLayer,
            showCountyBorders = countyBorders,
            showWaymarkedTrails = waymarkedTrails
        )
        mapViewProvider.setStyle(styleJson)
    }

    LaunchedEffect(Unit) {
        mapViewProvider.setShowsUserLocation(true)
    }

    MapScreenContent(
        snackbarHostState = snackbarHostState,
        isRecording = false,
        rulerState = rulerState,
        showLayerMenu = showLayerMenu,
        selectedLayer = currentLayer,
        showWaymarkedTrails = waymarkedTrails,
        showCountyBorders = countyBorders,
        showSavedPoints = showSavedPoints,
        onlineTrackingEnabled = false,
        recordingDistance = null,
        viewingTrackName = null,
        viewingPointName = null,
        viewingPointColor = "#FF5722",
        showViewingPoint = false,
        showSearch = false,
        searchQuery = "",
        searchResults = emptyList(),
        isSearching = false,
        onSearchClick = {},
        onLayerMenuToggle = { showLayerMenu = it },
        onLayerSelected = { currentLayer = it },
        onWaymarkedTrailsToggle = { waymarkedTrails = !waymarkedTrails },
        onCountyBordersToggle = { countyBorders = !countyBorders },
        onSavedPointsToggle = { showSavedPoints = !showSavedPoints },
        onRecordStopClick = {},
        onMyLocationClick = onMyLocationClick,
        onRulerToggle = {},
        onSettingsClick = onSettingsClick,
        onZoomIn = {},
        onZoomOut = {},
        onRulerUndo = {},
        onRulerClear = {},
        onRulerSaveAsTrack = {},
        onOnlineTrackingChange = {},
        onCloseViewingTrack = {},
        onCloseViewingPoint = {},
        onSearchQueryChange = {},
        onSearchResultClick = {},
        onSearchClose = {},
        mapContent = {
            UIKitView(
                factory = { mapViewProvider.createMapView() },
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
