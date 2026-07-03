package no.synth.where.data.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: LatLng): Double {
        val r = 6371000.0
        val dLat = (other.latitude - latitude).toRadians()
        val dLon = (other.longitude - longitude).toRadians()
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(latitude.toRadians()) * cos(other.latitude.toRadians()) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // Initial great-circle bearing from this point to [other], in degrees clockwise from north [0, 360).
    fun bearingTo(other: LatLng): Double {
        val lat1 = latitude.toRadians()
        val lat2 = other.latitude.toRadians()
        val dLon = (other.longitude - longitude).toRadians()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x).toDegrees() + 360.0) % 360.0
    }
}

data class LatLngBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    val latitudeSpan get() = north - south
    val longitudeSpan get() = east - west
    // Treat sub-cm spans (~1e-7° ≈ 1 cm) as a single point — guards against GPS jitter
    // producing sliver bboxes that would otherwise feed degenerate input to MapLibre.
    val isPoint get() = abs(latitudeSpan) < POINT_EPSILON && abs(longitudeSpan) < POINT_EPSILON
    val center get() = LatLng((south + north) / 2.0, (west + east) / 2.0)

    companion object {
        const val POINT_EPSILON = 1e-7
    }
}

fun List<LatLng>.bounds(): LatLngBounds? {
    if (isEmpty()) return null
    val valid = filter { it.latitude.isFinite() && it.longitude.isFinite() }
    if (valid.isEmpty()) return null
    var south = valid.first().latitude
    var north = valid.first().latitude
    var west = valid.first().longitude
    var east = valid.first().longitude
    for (p in valid) {
        if (p.latitude < south) south = p.latitude
        if (p.latitude > north) north = p.latitude
        if (p.longitude < west) west = p.longitude
        if (p.longitude > east) east = p.longitude
    }
    return LatLngBounds(south = south, west = west, north = north, east = east)
}

private fun Double.toRadians(): Double = this * PI / 180.0
private fun Double.toDegrees(): Double = this * 180.0 / PI
