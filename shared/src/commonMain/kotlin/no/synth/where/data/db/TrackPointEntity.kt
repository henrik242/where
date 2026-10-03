package no.synth.where.data.db

data class TrackPointEntity(
    val id: Long = 0,
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val orderIndex: Int
)
