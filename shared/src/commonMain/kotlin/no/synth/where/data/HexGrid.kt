package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds
import kotlin.math.floor
import kotlin.math.sqrt

object HexGrid {
    // Flat-top hexagons scaled for visual hexagonality at ~62°N (Norway center)
    // At 62°N: 1° lon ≈ 52km, 1° lat ≈ 111km
    // HEX_SIZE_LON: half-width (center to left/right vertex) in degrees longitude
    // HEX_SIZE_LAT: half-height (center to top/bottom flat edge) in degrees latitude
    // Ratio: HEX_SIZE_LAT = HEX_SIZE_LON * cos(62°) * sqrt(3)/2 ≈ HEX_SIZE_LON * 0.405
    const val HEX_SIZE_LON = 0.5    // ~52km wide hexagon
    val HEX_SIZE_LAT = HEX_SIZE_LON * (52.0 / 111.0) * sqrt(3.0) / 2  // ≈ 0.304°

    private val COL_STEP = HEX_SIZE_LON * 1.5   // ≈ 1.125° lon between column centers
    private val ROW_STEP = HEX_SIZE_LAT * 2      // ≈ 0.608° lat between row centers

    private const val ORIGIN_LON = 0.0
    private const val ORIGIN_LAT = 0.0

    data class Hex(val col: Int, val row: Int) {
        val id: String get() = "hex_${col}_${row}"
    }

    fun hexFromId(id: String): Hex? {
        if (!id.startsWith("hex_")) return null
        val rest = id.removePrefix("hex_")
        // Handle negative numbers: find the last underscore not at position 0
        val lastUnderscore = rest.lastIndexOf('_')
        if (lastUnderscore <= 0) return null
        return try {
            Hex(rest.substring(0, lastUnderscore).toInt(), rest.substring(lastUnderscore + 1).toInt())
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun hexCenter(hex: Hex): LatLng {
        val lon = ORIGIN_LON + hex.col * COL_STEP
        val latOffset = if (hex.col % 2 != 0) HEX_SIZE_LAT else 0.0
        val lat = ORIGIN_LAT + hex.row * ROW_STEP + latOffset
        return LatLng(lat, lon)
    }

    // Returns 7 vertices (first == last to close the polygon ring)
    fun hexVertices(hex: Hex): List<LatLng> {
        val center = hexCenter(hex)
        val lat = center.latitude
        val lon = center.longitude
        val w = HEX_SIZE_LON
        val h = HEX_SIZE_LAT
        return listOf(
            LatLng(lat, lon + w),           // right
            LatLng(lat + h, lon + w / 2),   // upper right
            LatLng(lat + h, lon - w / 2),   // upper left
            LatLng(lat, lon - w),           // left
            LatLng(lat - h, lon - w / 2),   // lower left
            LatLng(lat - h, lon + w / 2),   // lower right
            LatLng(lat, lon + w),           // close
        )
    }

    fun hexBounds(hex: Hex): LatLngBounds {
        val center = hexCenter(hex)
        return LatLngBounds(
            south = center.latitude - HEX_SIZE_LAT,
            west = center.longitude - HEX_SIZE_LON,
            north = center.latitude + HEX_SIZE_LAT,
            east = center.longitude + HEX_SIZE_LON,
        )
    }

    fun hexToRegion(hex: Hex): Region = Region(name = hex.id, boundingBox = hexBounds(hex))

    fun hexesInBounds(bounds: LatLngBounds): List<Hex> {
        val minCol = floor((bounds.west - ORIGIN_LON) / COL_STEP).toInt() - 1
        val maxCol = floor((bounds.east - ORIGIN_LON) / COL_STEP).toInt() + 1

        val result = mutableListOf<Hex>()
        for (col in minCol..maxCol) {
            val latOffset = if (col % 2 != 0) HEX_SIZE_LAT else 0.0
            val minRow = floor((bounds.south - ORIGIN_LAT - latOffset) / ROW_STEP).toInt() - 1
            val maxRow = floor((bounds.north - ORIGIN_LAT - latOffset) / ROW_STEP).toInt() + 1
            for (row in minRow..maxRow) {
                val hex = Hex(col, row)
                val center = hexCenter(hex)
                if (center.longitude + HEX_SIZE_LON >= bounds.west &&
                    center.longitude - HEX_SIZE_LON <= bounds.east &&
                    center.latitude + HEX_SIZE_LAT >= bounds.south &&
                    center.latitude - HEX_SIZE_LAT <= bounds.north
                ) {
                    result.add(hex)
                }
            }
        }
        return result
    }

    // Returns the hex whose center is nearest to the given point (Voronoi cell)
    fun hexAtPoint(lat: Double, lon: Double): Hex {
        val approxCol = (lon - ORIGIN_LON) / COL_STEP
        val colCenter = approxCol.toInt()

        var bestHex = Hex(colCenter, 0)
        var bestDist = Double.MAX_VALUE

        for (col in (colCenter - 2)..(colCenter + 2)) {
            val latOffset = if (col % 2 != 0) HEX_SIZE_LAT else 0.0
            val approxRow = (lat - ORIGIN_LAT - latOffset) / ROW_STEP
            val rowCenter = approxRow.toInt()
            for (row in (rowCenter - 1)..(rowCenter + 1)) {
                val hex = Hex(col, row)
                val center = hexCenter(hex)
                val dLon = (lon - center.longitude) / HEX_SIZE_LON
                val dLat = (lat - center.latitude) / HEX_SIZE_LAT
                val dist = dLon * dLon + dLat * dLat
                if (dist < bestDist) {
                    bestDist = dist
                    bestHex = hex
                }
            }
        }
        return bestHex
    }
}
