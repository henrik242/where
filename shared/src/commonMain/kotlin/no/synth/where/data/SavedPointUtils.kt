package no.synth.where.data

import no.synth.where.data.geo.LatLng

object SavedPointUtils {
    fun findNearestPoint(
        tapLocation: LatLng,
        savedPoints: List<SavedPoint>,
        maxDistanceMeters: Double = 500.0
    ): SavedPoint? {
        return savedPoints.minByOrNull { tapLocation.distanceTo(it.latLng) }
            ?.takeIf { tapLocation.distanceTo(it.latLng) < maxDistanceMeters }
    }
}
