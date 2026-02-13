package no.synth.where.data

import kotlinx.serialization.Serializable
import no.synth.where.data.serialization.LatLngSerializer
import no.synth.where.data.geo.LatLng
import no.synth.where.util.currentTimeMillis

@Serializable
data class SavedPoint(
    val id: String,
    val name: String,
    @Serializable(with = LatLngSerializer::class)
    val latLng: LatLng,
    val description: String? = "",
    val timestamp: Long = currentTimeMillis(),
    val color: String? = "#FF5722" // Default red color
)

