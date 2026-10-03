package no.synth.where.data.db

import no.synth.where.util.currentTimeMillis

data class SavedPointEntity(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = "",
    val timestamp: Long = currentTimeMillis(),
    val color: String? = "#FF5722"
)
