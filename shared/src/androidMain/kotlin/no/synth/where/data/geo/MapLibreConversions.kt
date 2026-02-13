package no.synth.where.data.geo

import org.maplibre.android.geometry.LatLng as MLLatLng
import org.maplibre.android.geometry.LatLngBounds as MLLatLngBounds

fun LatLng.toMapLibre() = MLLatLng(latitude, longitude)
fun MLLatLng.toCommon() = LatLng(latitude, longitude)

fun LatLngBounds.toMapLibre(): MLLatLngBounds =
    MLLatLngBounds.from(north, east, south, west)

fun MLLatLngBounds.toCommon() = LatLngBounds(
    south = latitudeSouth,
    west = longitudeWest,
    north = latitudeNorth,
    east = longitudeEast
)
