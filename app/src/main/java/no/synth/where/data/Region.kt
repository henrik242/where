package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds

data class Region(
    val name: String,
    val boundingBox: LatLngBounds,
    val polygon: List<List<LatLng>>? = null  // Actual fylke boundary as polygon coordinates
)

