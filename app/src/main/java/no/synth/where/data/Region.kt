package no.synth.where.data

import org.maplibre.android.geometry.LatLngBounds

data class Region(
    val name: String,
    val boundingBox: LatLngBounds
)

