package no.synth.where.ui.map

import platform.UIKit.UIView

interface MapViewProvider {
    fun createMapView(): UIView
    fun setStyle(json: String)
    fun setCamera(latitude: Double, longitude: Double, zoom: Double)
    fun setShowsUserLocation(show: Boolean)
    fun zoomIn()
    fun zoomOut()
    fun updateTrackLine(geoJson: String, color: String)
    fun clearTrackLine()
    fun getUserLocation(): List<Double>?
}
