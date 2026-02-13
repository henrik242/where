package no.synth.where.data

import no.synth.where.data.geo.LatLng

data class RulerPoint(
    val latLng: LatLng,
    val id: String = java.util.UUID.randomUUID().toString()
)

data class RulerState(
    val points: List<RulerPoint> = emptyList(),
    val isActive: Boolean = false
) {
    fun getTotalDistanceMeters(): Double {
        if (points.size < 2) return 0.0
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += points[i - 1].latLng.distanceTo(points[i].latLng)
        }
        return distance
    }

    fun getSegmentDistances(): List<Double> {
        if (points.size < 2) return emptyList()
        return points.zipWithNext { a, b ->
            a.latLng.distanceTo(b.latLng)
        }
    }

    fun addPoint(latLng: LatLng): RulerState {
        return copy(points = points + RulerPoint(latLng))
    }

    fun removeLastPoint(): RulerState {
        return if (points.isNotEmpty()) {
            copy(points = points.dropLast(1))
        } else this
    }

    fun clear(): RulerState {
        return copy(points = emptyList(), isActive = false)
    }
}

