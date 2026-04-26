package no.synth.where.ui.map

data class TwoFingerMeasurement(
    val screenX1: Float,
    val screenY1: Float,
    val screenX2: Float,
    val screenY2: Float,
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double,
    val distanceMeters: Double
) {
    fun reprojectedWith(project: (Double, Double) -> ScreenPoint?): TwoFingerMeasurement? {
        val s1 = project(lat1, lng1) ?: return null
        val s2 = project(lat2, lng2) ?: return null
        return copy(
            screenX1 = s1.x, screenY1 = s1.y,
            screenX2 = s2.x, screenY2 = s2.y
        )
    }
}
