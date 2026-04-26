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

fun interface MapTwoFingerTapCallback {
    fun onTwoFingerTap(
        screenX1: Float, screenY1: Float,
        screenX2: Float, screenY2: Float,
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
    fun updateTrackLine(geoJson: String, color: String)
    fun clearTrackLine()
    fun getUserLocation(): List<Double>?
    fun setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int)
    fun setCameraBounds(south: Double, west: Double, north: Double, east: Double, padding: Int, maxZoom: Int)
    fun updateSavedPoints(geoJson: String)
    fun clearSavedPoints()
    fun setOnLongPressCallback(callback: MapLongPressCallback?)
    fun setOnMapClickCallback(callback: MapClickCallback?)
    fun updateRuler(lineGeoJson: String, pointsGeoJson: String)
    fun clearRuler()
    fun updateSearchResults(geoJson: String)
    fun clearSearchResults()
    fun highlightSearchResult(geoJson: String)
    fun clearHighlightedSearchResult()
    fun updateFriendTrackLine(geoJson: String, color: String)
    fun clearFriendTrackLine()
    fun setConnected(connected: Boolean)
    fun getCameraCenter(): List<Double>?
    fun updateCoordGrid(geoJson: String)
    fun clearCoordGrid()
    fun setOnCameraMoveCallback(callback: MapCameraMoveCallback?)
    fun setOnTwoFingerTapCallback(callback: MapTwoFingerTapCallback?)
    fun projectToScreen(latitude: Double, longitude: Double): ScreenPoint?
}
