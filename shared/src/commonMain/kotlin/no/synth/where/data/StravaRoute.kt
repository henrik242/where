package no.synth.where.data

/**
 * Summary of a planned Strava route from GET /athletes/{id}/routes.
 * The full geometry is fetched separately via the GPX export endpoint.
 */
data class StravaRoute(
    val id: Long,
    val name: String,
    val distanceMeters: Double,
    val elevationGainMeters: Double,
    val starred: Boolean,
)
