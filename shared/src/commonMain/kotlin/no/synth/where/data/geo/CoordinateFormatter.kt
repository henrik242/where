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

enum class CoordFormat {
    UTM, MGRS, LATLNG;

    fun next(): CoordFormat = when (this) {
        LATLNG -> UTM
        UTM -> MGRS
        MGRS -> LATLNG
    }
}

object CoordinateFormatter {

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

    private data class UtmResult(val zone: Int, val letter: Char, val easting: Double, val northing: Double)

    private fun latLngToUtm(lat: Double, lon: Double): UtmResult {
        val a = 6378137.0
        val f = 1 / 298.257223563
        val k0 = 0.9996
        val e = sqrt(2 * f - f * f)
        val e2 = e * e
        val ep2 = e2 / (1 - e2)

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

        val lonOrigin = (zone - 1) * 6.0 - 180.0 + 3.0
        val latRad = lat * PI / 180.0
        val lonRad = (lon - lonOrigin) * PI / 180.0

        val n = a / sqrt(1 - e2 * sin(latRad) * sin(latRad))
        val t = tan(latRad) * tan(latRad)
        val c = ep2 * cos(latRad) * cos(latRad)
        val aCoeff = cos(latRad) * lonRad

        val m = a * (
            (1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latRad -
            (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * latRad) +
            (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * latRad) -
            (35 * e2 * e2 * e2 / 3072) * sin(6 * latRad)
        )

        var easting = k0 * n * (
            aCoeff +
            (1 - t + c) * aCoeff * aCoeff * aCoeff / 6 +
            (5 - 18 * t + t * t + 72 * c - 58 * ep2) * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff / 120
        ) + 500000.0

        var northing = k0 * (
            m + n * tan(latRad) * (
                aCoeff * aCoeff / 2 +
                (5 - t + 9 * c + 4 * c * c) * aCoeff * aCoeff * aCoeff * aCoeff / 24 +
                (61 - 58 * t + t * t + 600 * c - 330 * ep2) * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff * aCoeff / 720
            )
        )

        if (lat < 0) northing += 10000000.0

        val letter = utmLetterDesignator(lat)

        return UtmResult(zone, letter, easting, northing)
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
