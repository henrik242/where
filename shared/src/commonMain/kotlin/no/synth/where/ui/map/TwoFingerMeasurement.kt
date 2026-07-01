package no.synth.where.ui.map

data class TwoFingerMeasurement(
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double,
    val distanceMeters: Double,
) {
    val midLat: Double get() = (lat1 + lat2) / 2
    val midLng: Double get() = (lng1 + lng2) / 2
}
