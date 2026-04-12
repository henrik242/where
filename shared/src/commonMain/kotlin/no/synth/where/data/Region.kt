package no.synth.where.data

import no.synth.where.data.geo.LatLngBounds

data class Region(
    val name: String,
    val boundingBox: LatLngBounds,
)

