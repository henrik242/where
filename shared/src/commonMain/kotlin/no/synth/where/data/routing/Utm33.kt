package no.synth.where.data.routing

import no.synth.where.data.geo.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atanh
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/** Forward projection to ETRS89 / UTM zone 33N (EPSG:25833), Kartverket's national grid. */
internal object Utm33 {
    private const val A = 6378137.0
    private const val F = 1 / 298.257222101
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500000.0
    private const val CENTRAL_MERIDIAN = 15.0 * PI / 180

    // Krüger series to third order in n: sub-millimetre within a few zones of the central meridian.
    private const val N = F / (2 - F)
    private const val RECTIFYING_RADIUS = A / (1 + N) * (1 + N * N / 4)
    private val alpha = doubleArrayOf(
        N / 2 - 2 * N * N / 3 + 5 * N * N * N / 16,
        13 * N * N / 48 - 3 * N * N * N / 5,
        61 * N * N * N / 240,
    )
    private val e = 2 * sqrt(N) / (1 + N)

    /** Easting and northing in meters. */
    fun project(latLng: LatLng): Pair<Double, Double> {
        val phi = latLng.latitude * PI / 180
        val lambda = latLng.longitude * PI / 180 - CENTRAL_MERIDIAN
        val sinPhi = sin(phi)
        val t = sinh(atanh(sinPhi) - e * atanh(e * sinPhi))
        val xi = atan(t / cos(lambda))
        val eta = atanh(sin(lambda) / sqrt(1 + t * t))
        var x = eta
        var y = xi
        for (j in alpha.indices) {
            val k = 2.0 * (j + 1)
            x += alpha[j] * cos(k * xi) * sinh(k * eta)
            y += alpha[j] * sin(k * xi) * cosh(k * eta)
        }
        return FALSE_EASTING + K0 * RECTIFYING_RADIUS * x to K0 * RECTIFYING_RADIUS * y
    }
}
