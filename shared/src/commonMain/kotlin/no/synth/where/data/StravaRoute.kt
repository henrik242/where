package no.synth.where.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

/** A Strava object id. Prefers `id_str`, which Strava recommends now that ids are 64 bit. */
internal fun JsonObject.stravaId(): Long? =
    sequenceOf("id_str", "id")
        .mapNotNull { (this[it] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
        .firstOrNull()
