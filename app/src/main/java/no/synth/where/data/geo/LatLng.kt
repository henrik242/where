package no.synth.where.data.geo

import kotlin.math.PI
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
}

data class LatLngBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
) {
    val latitudeSpan get() = north - south
    val longitudeSpan get() = east - west

    companion object {
        fun from(north: Double, east: Double, south: Double, west: Double) =
            LatLngBounds(south = south, west = west, north = north, east = east)
    }
}

private fun Double.toRadians(): Double = this * PI / 180.0
