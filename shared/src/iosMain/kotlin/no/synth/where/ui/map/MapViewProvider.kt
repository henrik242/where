package no.synth.where.ui.map

import platform.UIKit.UIView

interface MapLongPressCallback {
    fun onLongPress(latitude: Double, longitude: Double)
}

interface MapClickCallback {
    fun onMapClick(latitude: Double, longitude: Double)
}

interface MapCameraMoveCallback {
    fun onCameraMove(latitude: Double, longitude: Double, zoom: Double, bearing: Double)
}

/** Reports the camera follow mode changing, including when a gesture drops it back to OFF. */
interface MapTrackingModeCallback {
    fun onTrackingModeChanged(mode: CameraFollowMode)
}

fun interface MapTwoFingerTapCallback {
    fun onTwoFingerTap(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    )
}

interface MapViewProvider {
    fun createMapView(): UIView
    fun setStyle(json: String)
    fun setCamera(latitude: Double, longitude: Double, zoom: Double)
    fun panTo(latitude: Double, longitude: Double)
    fun setShowsUserLocation(show: Boolean)
    fun zoomIn()
    fun zoomOut()
    /**
     * Render any number of track lines from a single FeatureCollection whose features carry their
     * own `color`/`width`/`opacity` properties (see buildTracksGeoJson). An empty collection clears
     * them. Shares the track source/layer with [clearTrackLine].
     */
    fun updateTracks(geoJson: String)
    fun clearTrackLine()
    fun getUserLocation(): List<Double>?
    fun setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int)
    fun setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int, maxZoom: Int)
    fun updateSavedPoints(geoJson: String)
    fun clearSavedPoints()
    /** Single elevation-scrub marker point; an empty FeatureCollection clears it. */
    fun updateElevationMarker(geoJson: String)
    fun setOnLongPressCallback(callback: MapLongPressCallback?)
    fun setOnMapClickCallback(callback: MapClickCallback?)
    fun updateRuler(lineGeoJson: String, pointsGeoJson: String)
    fun clearRuler()
    fun updateMeasurement(lineGeoJson: String, pointsGeoJson: String)
    fun fadeMeasurement(durationMs: Double)
    fun clearMeasurement()
    fun updateSearchResults(geoJson: String)
    fun clearSearchResults()
    fun highlightSearchResult(geoJson: String)
    fun clearHighlightedSearchResult()
    fun updateFriendTrackLine(geoJson: String, color: String)
    fun clearFriendTrackLine()
    fun updateNavigation(completedGeoJson: String, remainingGeoJson: String, offCourseGeoJson: String?)
    fun clearNavigation()
    fun setConnected(connected: Boolean)
    fun getCameraCenter(): List<Double>?
    fun updateCoordGrid(geoJson: String)
    fun clearCoordGrid()
    fun setOnCameraMoveCallback(callback: MapCameraMoveCallback?)
    fun setOnTwoFingerTapCallback(callback: MapTwoFingerTapCallback?)
    fun setCameraFollowMode(mode: CameraFollowMode)
    fun setOnTrackingModeCallback(callback: MapTrackingModeCallback?)
}
