package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.util.currentTimeMillis

/**
 * A named marker shared with live-tracking followers (issue #99). Lives on the tracking server
 * for the duration of the sharer's session, keyed to [ownerClientId]. Distinct from [SavedPoint],
 * which is a private on-device waypoint; a follower can copy a received shared point into their
 * own saved points.
 */
data class SharedPoint(
    val id: String,
    val ownerClientId: String,
    val name: String,
    val description: String = "",
    val latLng: LatLng,
    val color: String = "#FF5722",
    val timestamp: Long = currentTimeMillis(),
)
