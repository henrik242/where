package no.synth.where.data.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import no.synth.where.util.roundToDecimals

data class UtmResult(val zone: Int, val letter: Char, val easting: Double, val northing: Double)

enum class CoordFormat {
    UTM, MGRS, LATLNG;

    fun next(): CoordFormat = when (this) {
        LATLNG -> UTM
        UTM -> MGRS
        MGRS -> LATLNG
    }
}

object CoordinateFormatter {

    // WGS84 ellipsoid constants
    private const val UTM_A = 6378137.0
    private const val UTM_F = 1 / 298.257223563
    private const val UTM_K0 = 0.9996
    private val UTM_E = sqrt(2 * UTM_F - UTM_F * UTM_F)
    private val UTM_E2 = UTM_E * UTM_E
    private val UTM_EP2 = UTM_E2 / (1 - UTM_E2)
    private val UTM_E1 = (1 - sqrt(1 - UTM_E2)) / (1 + sqrt(1 - UTM_E2))

    fun formatLatLng(latLng: LatLng): String {
        val latDir = if (latLng.latitude >= 0) "N" else "S"
        val lonDir = if (latLng.longitude >= 0) "E" else "W"
        val latStr = abs(latLng.latitude).roundToDecimals(4)
        val lonStr = abs(latLng.longitude).roundToDecimals(4)
        return "$latStr° $latDir, $lonStr° $lonDir"
    }

    fun formatUtm(latLng: LatLng): String {
        val (zone, letter, easting, northing) = latLngToUtm(latLng.latitude, latLng.longitude)
        return "${zone}${letter} ${easting.roundToInt()} ${northing.roundToInt()}"
    }

    fun formatMgrs(latLng: LatLng): String {
        val (zone, letter, easting, northing) = latLngToUtm(latLng.latitude, latLng.longitude)
        val gridLetters = mgrsGridLetters(zone, easting, northing)
        val e5 = (easting.roundToInt() % 100000).toString().padStart(5, '0')
        val n5 = (northing.roundToInt() % 100000).toString().padStart(5, '0')
        return "${zone}${letter} $gridLetters $e5 $n5"
    }

    private fun mgrsGridLetters(zone: Int, easting: Double, northing: Double): String {
        val setNumber = ((zone - 1) % 6)
        val colLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ" // 24 letters, no I or O
        val colIndex = ((easting / 100000).toInt() - 1) // 1-based column
        val colOffset = (setNumber % 3) * 8
        val col = colLetters[(colOffset + colIndex) % 24]

        val rowLetters = if (setNumber % 2 == 0) {
            "ABCDEFGHJKLMNPQRSTUV" // 20 letters, no I or O
        } else {
            "FGHJKLMNPQRSTUVABCDE" // offset by 5
        }
        val rowIndex = (northing.roundToInt() / 100000) % 20
        val row = rowLetters[rowIndex]

        return "$col$row"
    }

    fun latLngToUtm(lat: Double, lon: Double): UtmResult {
        val zone = computeUtmZone(lat, lon)
        return latLngToUtmInZone(lat, lon, zone)
    }

    fun latLngToUtmInZone(lat: Double, lon: Double, zone: Int): UtmResult {
        val lonOrigin = (zone - 1) * 6.0 - 180.0 + 3.0
        val latRad = lat * PI / 180.0
        val lonRad = (lon - lonOrigin) * PI / 180.0

        val n = UTM_A / sqrt(1 - UTM_E2 * sin(latRad) * sin(latRad))
        val t = tan(latRad) * tan(latRad)
        val c = UTM_EP2 * cos(latRad) * cos(latRad)
        val aCoeff = cos(latRad) * lonRad

        val m = UTM_A * (
            (1 - UTM_E2 / 4 - 3 * UTM_E2 * UTM_E2 / 64 - 5 * UTM_E2 * UTM_E2 * UTM_E2 / 256) * latRad -
            (3 * UTM_E2 / 8 + 3 * UTM_E2 * UTM_E2 / 32 + 45 * UTM_E2 * UTM_E2 * UTM_E2 / 1024) * sin(2 * latRad) +
            (15 * UTM_E2 * UTM_E2 / 256 + 45 * UTM_E2 * UTM_E2 * UTM_E2 / 1024) * sin(4 * latRad) -
            (35 * UTM_E2 * UTM_E2 * UTM_E2 / 3072) * sin(6 * latRad)
        )

        var easting = UTM_K0 * n * (
            aCoeff +
            (1 - t + c) * aCoeff * aCoeff * aCoeff / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * UTM_EP2) * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff / 120
        ) + 500000.0

        var northing = UTM_K0 * (
            m + n * tan(latRad) * (
                aCoeff * aCoeff / 2 +
                (5 - t + 9 * c + 4 * c * c) * aCoeff * aCoeff * aCoeff * aCoeff / 24 +
                (61 - 58 * t + t * t + 600 * c - 330 * UTM_EP2) * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff / 720
            )
        )

        if (lat < 0) northing += 10000000.0

        val letter = utmLetterDesignator(lat)

        return UtmResult(zone, letter, easting, northing)
    }

    private fun computeUtmZone(lat: Double, lon: Double): Int {
        var zone = floor((lon + 180.0) / 6.0).toInt() + 1

        // Norway special zones
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) zone = 32
        if (lat >= 72.0 && lat < 84.0) {
            when {
                lon >= 0.0 && lon < 9.0 -> zone = 31
                lon >= 9.0 && lon < 21.0 -> zone = 33
                lon >= 21.0 && lon < 33.0 -> zone = 35
                lon >= 33.0 && lon < 42.0 -> zone = 37
            }
        }
        return zone
    }

    fun utmToLatLng(zone: Int, easting: Double, northing: Double, northern: Boolean): LatLng {
        val x = easting - 500000.0
        val y = if (northern) northing else northing - 10000000.0

        val m = y / UTM_K0
        val mu = m / (UTM_A * (1 - UTM_E2 / 4 - 3 * UTM_E2 * UTM_E2 / 64 - 5 * UTM_E2 * UTM_E2 * UTM_E2 / 256))

        val phi1 = mu +
            (3 * UTM_E1 / 2 - 27 * UTM_E1 * UTM_E1 * UTM_E1 / 32) * sin(2 * mu) +
            (21 * UTM_E1 * UTM_E1 / 16 - 55 * UTM_E1 * UTM_E1 * UTM_E1 * UTM_E1 / 32) * sin(4 * mu) +
            (151 * UTM_E1 * UTM_E1 * UTM_E1 / 96) * sin(6 * mu) +
            (1097 * UTM_E1 * UTM_E1 * UTM_E1 * UTM_E1 / 512) * sin(8 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val n1 = UTM_A / sqrt(1 - UTM_E2 * sinPhi1 * sinPhi1)
        val t1 = tanPhi1 * tanPhi1
        val c1 = UTM_EP2 * cosPhi1 * cosPhi1
        val denom = 1 - UTM_E2 * sinPhi1 * sinPhi1
        val r1 = UTM_A * (1 - UTM_E2) / (denom * sqrt(denom))
        val d = x / (n1 * UTM_K0)

        val latRad = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2 -
            (5 + 3 * t1 + 10 * c1 - 4 * c1 * c1 - 9 * UTM_EP2) * d * d * d * d / 24 +
            (61 + 90 * t1 + 298 * c1 + 45 * t1 * t1 - 252 * UTM_EP2 - 3 * c1 * c1) * d * d * d * d * d * d / 720
        )

        val lonOrigin = (zone - 1) * 6.0 - 180.0 + 3.0
        val lonDiffRad = (
            d -
            (1 + 2 * t1 + c1) * d * d * d / 6 +
            (5 - 2 * c1 + 28 * t1 - 3 * c1 * c1 + 8 * UTM_EP2 + 24 * t1 * t1) * d * d * d * d * d / 120
        ) / cosPhi1

        return LatLng(latRad * 180.0 / PI, lonOrigin + lonDiffRad * 180.0 / PI)
    }

    private fun utmLetterDesignator(lat: Double): Char = when {
        lat >= 72 -> 'X'
        lat >= 64 -> 'W'
        lat >= 56 -> 'V'
        lat >= 48 -> 'U'
        lat >= 40 -> 'T'
        lat >= 32 -> 'S'
        lat >= 24 -> 'R'
        lat >= 16 -> 'Q'
        lat >= 8 -> 'P'
        lat >= 0 -> 'N'
        lat >= -8 -> 'M'
        lat >= -16 -> 'L'
        lat >= -24 -> 'K'
        lat >= -32 -> 'J'
        lat >= -40 -> 'H'
        lat >= -48 -> 'G'
        lat >= -56 -> 'F'
        lat >= -64 -> 'E'
        lat >= -72 -> 'D'
        lat >= -80 -> 'C'
        else -> 'Z'
    }
}
