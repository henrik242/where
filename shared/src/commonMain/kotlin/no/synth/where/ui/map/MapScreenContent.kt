package no.synth.where.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.LatLng

@Composable
fun MapScreenContent(
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    // FAB state
    isRecording: Boolean,
    rulerState: RulerState,
    showLayerMenu: Boolean,
    selectedLayer: MapLayer,
    showWaymarkedTrails: Boolean,
    showCountyBorders: Boolean,
    showSavedPoints: Boolean,
    showAvalancheZones: Boolean,
    showHillshade: Boolean = false,
    // Crosshair state
    crosshairActive: Boolean = false,
    crosshairInfo: CrosshairInfo = CrosshairInfo(),
    centerLatLng: LatLng? = null,
    coordFormat: CoordFormat = CoordFormat.LATLNG,
    onToggleCoordFormat: () -> Unit = {},
    onCrosshairToggle: () -> Unit = {},
    // Overlay state
    offlineModeEnabled: Boolean = false,
    onlineTrackingEnabled: Boolean,
    recordingDistance: Double?,
    viewingTrackName: String?,
    viewingPointName: String?,
    viewingPointColor: String,
    showViewingPoint: Boolean,
    showSearch: Boolean,
    searchQuery: String,
    searchResults: List<PlaceSearchClient.SearchResult>,
    searchHistory: List<PlaceSearchClient.SearchResult> = emptyList(),
    isSearching: Boolean,
    // FAB callbacks
    onSearchClick: () -> Unit,
    onLayerMenuToggle: (Boolean) -> Unit,
    onLayerSelected: (MapLayer) -> Unit,
    onWaymarkedTrailsToggle: () -> Unit,
    onCountyBordersToggle: () -> Unit,
    onSavedPointsToggle: () -> Unit,
    onAvalancheZonesToggle: () -> Unit,
    onHillshadeToggle: () -> Unit = {},
    onRecordStopClick: () -> Unit,
    onMyLocationClick: () -> Unit,
    onRulerToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    // Overlay callbacks
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingChange: (Boolean) -> Unit,
    onOnlineTrackingClick: () -> Unit = {},
    onOfflineIndicatorClick: () -> Unit = {},
    onCloseViewingTrack: () -> Unit,
    onCloseViewingPoint: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchResultHover: (PlaceSearchClient.SearchResult?) -> Unit = {},
    onSearchClose: () -> Unit,
    // Map slot
    mapContent: @Composable () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            MapFabColumn(
                isRecording = isRecording,
                rulerActive = rulerState.isActive,
                crosshairActive = crosshairActive,
                showLayerMenu = showLayerMenu,
                selectedLayer = selectedLayer,
                showWaymarkedTrails = showWaymarkedTrails,
                showCountyBorders = showCountyBorders,
                showSavedPoints = showSavedPoints,
                showAvalancheZones = showAvalancheZones,
                showHillshade = showHillshade,
                onSearchClick = onSearchClick,
                onLayerMenuToggle = onLayerMenuToggle,
                onLayerSelected = onLayerSelected,
                onWaymarkedTrailsToggle = onWaymarkedTrailsToggle,
                onCountyBordersToggle = onCountyBordersToggle,
                onSavedPointsToggle = onSavedPointsToggle,
                onAvalancheZonesToggle = onAvalancheZonesToggle,
                onHillshadeToggle = onHillshadeToggle,
                onRecordStopClick = onRecordStopClick,
                onMyLocationClick = onMyLocationClick,
                onRulerToggle = onRulerToggle,
                onCrosshairToggle = onCrosshairToggle,
                onSettingsClick = onSettingsClick
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            mapContent()

            MapOverlays(
                offlineModeEnabled = offlineModeEnabled,
                crosshairActive = crosshairActive,
                crosshairInfo = crosshairInfo,
                centerLatLng = centerLatLng,
                coordFormat = coordFormat,
                onToggleCoordFormat = onToggleCoordFormat,
                rulerState = rulerState,
                isRecording = isRecording,
                recordingDistance = recordingDistance,
                onlineTrackingEnabled = onlineTrackingEnabled,
                viewingTrackName = viewingTrackName,
                viewingPointName = viewingPointName,
                viewingPointColor = viewingPointColor,
                showSearch = showSearch,
                searchQuery = searchQuery,
                searchResults = searchResults,
                searchHistory = searchHistory,
                isSearching = isSearching,
                showViewingPoint = showViewingPoint,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onRulerUndo = onRulerUndo,
                onRulerClear = onRulerClear,
                onRulerSaveAsTrack = onRulerSaveAsTrack,
                onOnlineTrackingChange = onOnlineTrackingChange,
                onOnlineTrackingClick = onOnlineTrackingClick,
                onOfflineIndicatorClick = onOfflineIndicatorClick,
                onCloseViewingTrack = onCloseViewingTrack,
                onCloseViewingPoint = onCloseViewingPoint,
                onSearchQueryChange = onSearchQueryChange,
                onSearchResultClick = onSearchResultClick,
                onSearchResultHover = onSearchResultHover,
                onSearchClose = onSearchClose
            )
        }
    }
}
