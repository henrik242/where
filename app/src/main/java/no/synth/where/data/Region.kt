package no.synth.where.data

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

data class Region(
    val name: String,
    val boundingBox: LatLngBounds,
    val polygon: List<List<LatLng>>? = null  // Actual fylke boundary as polygon coordinates
)

