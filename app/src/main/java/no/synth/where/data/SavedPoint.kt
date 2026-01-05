package no.synth.where.data

import org.maplibre.android.geometry.LatLng

data class SavedPoint(
    val id: String,
    val name: String,
    val latLng: LatLng,
    val description: String? = "",
    val timestamp: Long = System.currentTimeMillis(),
    val color: String? = "#FF5722" // Default red color
)

