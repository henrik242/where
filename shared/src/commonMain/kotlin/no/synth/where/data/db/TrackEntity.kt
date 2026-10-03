package no.synth.where.data.db

data class TrackEntity(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val isRecording: Boolean = false,
    // null = unfiled; the exact (case-sensitive) name is the folder's identity; one folder per track.
    val folder: String? = null,
    // Stable external origin id (e.g. "strava:route:123") used to dedupe re-imports; null for local tracks.
    val sourceId: String? = null,
    // User-chosen line color (hex); null = auto palette color.
    val color: String? = null
)
