package no.synth.where.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import no.synth.where.data.CrosshairInfo
import no.synth.where.data.PlaceSearchClient
import no.synth.where.data.RulerState
import no.synth.where.data.Track
import no.synth.where.data.TrackCropState
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
    showSavedPoints: Boolean,
    showAvalancheZones: Boolean,
    showHillshade: Boolean = false,
    showCoordGrid: Boolean = false,
    // Crosshair state
    crosshairActive: Boolean = false,
    crosshairInfo: CrosshairInfo = CrosshairInfo(),
    centerLatLng: LatLng? = null,
    userLocation: LatLng? = null,
    coordFormat: CoordFormat = CoordFormat.LATLNG,
    onToggleCoordFormat: () -> Unit = {},
    onCrosshairToggle: () -> Unit = {},
    // Overlay state
    offlineModeEnabled: Boolean = false,
    isLocating: Boolean = false,
    onlineTrackingEnabled: Boolean,
    viewerCount: Int = 0,
    liveShareUntilMillis: Long = 0L,
    isLiveSharing: Boolean = false,
    recordingDistance: Double?,
    viewingTracks: List<Track> = emptyList(),
    focusedTrackId: String? = null,
    cropState: TrackCropState? = null,
    onCropChange: (Int, Int) -> Unit = { _, _ -> },
    onCancelCrop: () -> Unit = {},
    onApplyCrop: () -> Unit = {},
    elevationMarker: Int? = null,
    onElevationScrub: (Int?) -> Unit = {},
    navigation: NavigationUiState = NavigationUiState(),
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
    onSavedPointsToggle: () -> Unit,
    onAvalancheZonesToggle: () -> Unit,
    onHillshadeToggle: () -> Unit = {},
    onCoordGridToggle: () -> Unit = {},
    onRecordStopClick: () -> Unit,
    cameraFollowMode: CameraFollowMode = CameraFollowMode.OFF,
    onMyLocationClick: () -> Unit,
    onRulerToggle: () -> Unit,
    onSettingsClick: () -> Unit,
    // Overlay callbacks
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRulerUndo: () -> Unit,
    onRulerClear: () -> Unit,
    onRulerSaveAsTrack: () -> Unit,
    onOnlineTrackingClick: () -> Unit = {},
    onOfflineIndicatorClick: () -> Unit = {},
    onCloseTrack: () -> Unit,
    onCollapseTrack: () -> Unit = {},
    onStartNavigation: () -> Unit = {},
    onCloseViewingPoint: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchResultClick: (PlaceSearchClient.SearchResult) -> Unit,
    onSearchResultHover: (PlaceSearchClient.SearchResult?) -> Unit = {},
    onSearchClose: () -> Unit,
    // Follow friend state
    followedClientId: String? = null,
    isFollowConnecting: Boolean = false,
    isFollowedTrackActive: Boolean = false,
    onFollowBannerClick: () -> Unit = {},
    onStopFollowing: () -> Unit = {},
    // Map slot
    mapContent: @Composable () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            // Hidden only while a track is focused. startNavigation() clears focusedTrackId,
            // so the FAB column stays available during navigation.
            AnimatedVisibility(
                visible = focusedTrackId == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MapFabColumn(
                    isRecording = isRecording,
                    rulerActive = rulerState.isActive,
                    crosshairActive = crosshairActive,
                    showLayerMenu = showLayerMenu,
                    selectedLayer = selectedLayer,
                    showWaymarkedTrails = showWaymarkedTrails,
                    showSavedPoints = showSavedPoints,
                    showAvalancheZones = showAvalancheZones,
                    showHillshade = showHillshade,
                    showCoordGrid = showCoordGrid,
                    showRecordFab = !navigation.isNavigating,
                    onSearchClick = onSearchClick,
                    onLayerMenuToggle = onLayerMenuToggle,
                    onLayerSelected = onLayerSelected,
                    onWaymarkedTrailsToggle = onWaymarkedTrailsToggle,
                    onSavedPointsToggle = onSavedPointsToggle,
                    onAvalancheZonesToggle = onAvalancheZonesToggle,
                    onHillshadeToggle = onHillshadeToggle,
                    onCoordGridToggle = onCoordGridToggle,
                    onRecordStopClick = onRecordStopClick,
                    cameraFollowMode = cameraFollowMode,
                    onMyLocationClick = onMyLocationClick,
                    onRulerToggle = onRulerToggle,
                    onCrosshairToggle = onCrosshairToggle,
                    onSettingsClick = onSettingsClick
                )
            }
        }
    ) { paddingValues ->
        // Height of the bottom-center altitude/crop chart (0 when none), reported by MapOverlays so
        // the snackbar can sit above it instead of covering it.
        var bottomChartHeight by remember { mutableStateOf(0.dp) }
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            mapContent()

            MapOverlays(
                offlineModeEnabled = offlineModeEnabled,
                isLocating = isLocating,
                crosshairActive = crosshairActive,
                crosshairInfo = crosshairInfo,
                centerLatLng = centerLatLng,
                userLocation = userLocation,
                coordFormat = coordFormat,
                onToggleCoordFormat = onToggleCoordFormat,
                rulerState = rulerState,
                isRecording = isRecording,
                recordingDistance = recordingDistance,
                onlineTrackingEnabled = onlineTrackingEnabled,
                viewerCount = viewerCount,
                liveShareUntilMillis = liveShareUntilMillis,
                isLiveSharing = isLiveSharing,
                viewingTracks = viewingTracks,
                focusedTrackId = focusedTrackId,
                cropState = cropState,
                onCropChange = onCropChange,
                onCancelCrop = onCancelCrop,
                onApplyCrop = onApplyCrop,
                elevationMarker = elevationMarker,
                onElevationScrub = onElevationScrub,
                onBottomChartHeightChanged = { bottomChartHeight = it },
                navigation = navigation,
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
                onOnlineTrackingClick = onOnlineTrackingClick,
                onOfflineIndicatorClick = onOfflineIndicatorClick,
                onCloseTrack = onCloseTrack,
                onCollapseTrack = onCollapseTrack,
                onStartNavigation = onStartNavigation,
                onCloseViewingPoint = onCloseViewingPoint,
                onSearchQueryChange = onSearchQueryChange,
                onSearchResultClick = onSearchResultClick,
                onSearchResultHover = onSearchResultHover,
                onSearchClose = onSearchClose,
                followedClientId = followedClientId,
                isFollowConnecting = isFollowConnecting,
                isFollowedTrackActive = isFollowedTrackActive,
                onFollowBannerClick = onFollowBannerClick,
                onStopFollowing = onStopFollowing
            )

            // Pinned bottom-left, left of the FAB column, so snackbars sit at the bottom of the
            // screen rather than floating mid-screen above the tall FAB stack. Lifted above the
            // bottom chart when one is shown so it doesn't cover it (e.g. the crop-undo snackbar).
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, end = 80.dp, bottom = bottomChartHeight)
            )
        }
    }
}
