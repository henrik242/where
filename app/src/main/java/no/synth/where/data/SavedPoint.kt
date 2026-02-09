package no.synth.where.data

import kotlinx.serialization.Serializable
import no.synth.where.data.serialization.LatLngSerializer
import org.maplibre.android.geometry.LatLng

@Serializable
data class SavedPoint(
    val id: String,
    val name: String,
    @Serializable(with = LatLngSerializer::class)
    val latLng: LatLng,
    val description: String? = "",
    val timestamp: Long = System.currentTimeMillis(),
    val color: String? = "#FF5722" // Default red color
)

