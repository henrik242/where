package no.synth.where.ui.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import no.synth.where.data.geo.CoordFormat
import no.synth.where.data.geo.CoordinateFormatter
import no.synth.where.data.geo.LatLng
import no.synth.where.data.geo.LatLngBounds

object CoordGrid {

    fun buildGeoJson(centerLat: Double, centerLng: Double, zoom: Double, format: CoordFormat): String {
        return when (format) {
            CoordFormat.LATLNG -> buildLatLngGrid(centerLat, centerLng, zoom)
            CoordFormat.UTM, CoordFormat.MGRS -> buildUtmGrid(centerLat, centerLng, zoom)
        }
    }

    private fun lineBounds(centerLat: Double, centerLng: Double, zoom: Double): LatLngBounds {
        val halfSpan = 360.0 / 2.0.pow(zoom) * 4.0
        return LatLngBounds(
            south = (centerLat - halfSpan).coerceIn(-80.0, 84.0),
            north = (centerLat + halfSpan).coerceIn(-80.0, 84.0),
            west = (centerLng - halfSpan).coerceAtLeast(-180.0),
            east = (centerLng + halfSpan).coerceAtMost(180.0)
        )
    }

    private fun utmGridBounds(centerLat: Double, centerLng: Double, spacing: Double): LatLngBounds {
        val halfMeters = spacing * 80.0
        val halfLat = halfMeters / 111320.0
        val cosLat = cos(centerLat * PI / 180.0).coerceIn(0.1, 1.0)
        val halfLng = halfMeters / (111320.0 * cosLat)
        return LatLngBounds(
            south = (centerLat - halfLat).coerceIn(-80.0, 84.0),
            north = (centerLat + halfLat).coerceIn(-80.0, 84.0),
            west = (centerLng - halfLng).coerceAtLeast(-180.0),
            east = (centerLng + halfLng).coerceAtMost(180.0)
        )
    }

    private fun labelOffset(zoom: Double): Double = 360.0 / 2.0.pow(zoom) * 0.3

    private fun latLngGridSpacing(zoom: Double): Double = when {
        zoom < 3 -> 30.0
        zoom < 5 -> 10.0
        zoom < 7 -> 5.0
        zoom < 8 -> 2.0
        zoom < 9 -> 1.0
        zoom < 10 -> 0.5
        zoom < 12 -> 0.1
        zoom < 14 -> 0.05
        zoom < 16 -> 0.01
        zoom < 18 -> 0.005
        else -> 0.001
    }

    private fun utmGridSpacing(zoom: Double): Double = when {
        zoom < 7 -> -1.0
        zoom < 10 -> 10000.0
        else -> 1000.0
    }

    // --- Lat/Lng grid ---

    private fun buildLatLngGrid(centerLat: Double, centerLng: Double, zoom: Double): String {
        val bounds = lineBounds(centerLat, centerLng, zoom)
        val spacing = latLngGridSpacing(zoom)
        val decimals = coordDecimals(spacing)
        val off = labelOffset(zoom)
        val labelLng = centerLng - off
        val labelLat = centerLat - off
        val f = Features()

        val startLat = floor(bounds.south / spacing) * spacing
        var i = 0
        while (startLat + i * spacing <= bounds.north) {
            val lat = startLat + i * spacing
            f.addLine("[[${bounds.west},$lat],[${bounds.east},$lat]]")
            f.addLabel(labelLng, lat, formatLatLabel(lat, decimals), "right")
            i++
        }

        val startLng = floor(bounds.west / spacing) * spacing
        i = 0
        while (startLng + i * spacing <= bounds.east) {
            val lng = startLng + i * spacing
            f.addLine("[[$lng,${bounds.south}],[$lng,${bounds.north}]]")
            f.addLabel(lng, labelLat, formatLngLabel(lng, decimals), "top")
            i++
        }

        return f.toGeoJson()
    }

    // --- UTM/MGRS grid ---

    private fun buildUtmGrid(centerLat: Double, centerLng: Double, zoom: Double): String {
        val spacing = utmGridSpacing(zoom)
        val f = Features()

        val viewBounds = lineBounds(centerLat, centerLng, zoom)
        appendZoneBoundaries(f, viewBounds)

        if (spacing > 0) {
            val bounds = utmGridBounds(centerLat, centerLng, spacing)
            val zone = CoordinateFormatter.latLngToUtm(centerLat, centerLng).zone
            val off = labelOffset(zoom)

            if (bounds.south < 0 && bounds.north > 0) {
                val southBounds = LatLngBounds(bounds.south, bounds.west, 0.0, bounds.east)
                val northBounds = LatLngBounds(0.0, bounds.west, bounds.north, bounds.east)
                appendUtmZoneGrid(f, southBounds, spacing, false, zone, centerLat, centerLng, off)
                appendUtmZoneGrid(f, northBounds, spacing, true, zone, centerLat, centerLng, off)
            } else {
                appendUtmZoneGrid(f, bounds, spacing, centerLat >= 0, zone, centerLat, centerLng, off)
            }
        }

        return f.toGeoJson()
    }

    private fun appendZoneBoundaries(f: Features, bounds: LatLngBounds) {
        val startLng = floor(bounds.west / 6.0) * 6.0
        var i = 0
        while (true) {
            val lng = startLng + i * 6.0
            if (lng > bounds.east) break
            i++
            if (lng < bounds.west) continue

            f.addZoneLine("[[$lng,${bounds.south}],[$lng,${bounds.north}]]")

            val zone = utmZone(lng + 3.0)
            val labelLng = (lng + 3.0).coerceIn(bounds.west, bounds.east)
            f.addLabel(labelLng, bounds.north, "$zone", "top")
        }
    }

    private fun appendUtmZoneGrid(
        f: Features,
        bounds: LatLngBounds,
        spacing: Double,
        northern: Boolean,
        zone: Int,
        centerLat: Double,
        centerLng: Double,
        labelOffset: Double
    ) {
        val zoneWest = (zone - 1) * 6.0 - 180.0
        val zoneEast = zoneWest + 6.0

        val effectiveWest = maxOf(bounds.west, zoneWest)
        val effectiveEast = minOf(bounds.east, zoneEast)
        val effectiveSouth = bounds.south.coerceIn(-80.0, 84.0)
        val effectiveNorth = bounds.north.coerceIn(-80.0, 84.0)

        if (effectiveWest >= effectiveEast || effectiveSouth >= effectiveNorth) return

        val swUtm = CoordinateFormatter.latLngToUtmInZone(effectiveSouth, effectiveWest, zone)
        val neUtm = CoordinateFormatter.latLngToUtmInZone(effectiveNorth, effectiveEast, zone)
        val nwUtm = CoordinateFormatter.latLngToUtmInZone(effectiveNorth, effectiveWest, zone)
        val seUtm = CoordinateFormatter.latLngToUtmInZone(effectiveSouth, effectiveEast, zone)

        val minEasting = minOf(swUtm.easting, nwUtm.easting)
        val maxEasting = maxOf(seUtm.easting, neUtm.easting)
        val minNorthing = minOf(swUtm.northing, seUtm.northing)
        val maxNorthing = maxOf(nwUtm.northing, neUtm.northing)

        // Label placement near bottom-left of visible area
        val labelSouthLat = (centerLat - labelOffset).coerceIn(effectiveSouth, effectiveNorth)
        val labelWestLng = (centerLng - labelOffset).coerceIn(effectiveWest, effectiveEast)
        val labelUtm = CoordinateFormatter.latLngToUtmInZone(labelSouthLat, labelWestLng, zone)

        val numSamples = 20

        // Constant easting lines (roughly north-south)
        val startE = floor(minEasting / spacing) * spacing
        var idx = 0
        while (true) {
            val e = startE + idx * spacing
            if (e > maxEasting) break
            idx++
            val coords = buildLineCoords(numSamples) { i ->
                val n = minNorthing + (maxNorthing - minNorthing) * i / numSamples
                CoordinateFormatter.utmToLatLng(zone, e, n, northern)
            } ?: continue
            f.addLine("[$coords]")
            val lp = CoordinateFormatter.utmToLatLng(zone, e, labelUtm.northing, northern)
            f.addLabel(lp.longitude, lp.latitude, formatUtmValue(e, spacing), "top")
        }

        // Constant northing lines (roughly east-west)
        val startN = floor(minNorthing / spacing) * spacing
        idx = 0
        while (true) {
            val n = startN + idx * spacing
            if (n > maxNorthing) break
            idx++
            val coords = buildLineCoords(numSamples) { i ->
                val eVal = minEasting + (maxEasting - minEasting) * i / numSamples
                CoordinateFormatter.utmToLatLng(zone, eVal, n, northern)
            } ?: continue
            f.addLine("[$coords]")
            val lp = CoordinateFormatter.utmToLatLng(zone, labelUtm.easting, n, northern)
            f.addLabel(lp.longitude, lp.latitude, formatUtmValue(n, spacing), "right")
        }
    }

    // --- Line sampling ---

    private inline fun buildLineCoords(
        numSamples: Int,
        pointAt: (Int) -> LatLng
    ): String? {
        val sb = StringBuilder()
        var pointCount = 0
        for (i in 0..numSamples) {
            val ll = pointAt(i)
            if (ll.latitude < -85 || ll.latitude > 85) continue
            if (pointCount > 0) sb.append(",")
            pointCount++
            sb.append("[${ll.longitude},${ll.latitude}]")
        }
        return if (pointCount >= 2) sb.toString() else null
    }

    // --- Label formatting ---

    private fun formatLatLabel(lat: Double, decimals: Int): String {
        val dir = if (lat >= 0) "N" else "S"
        return "${formatCoord(lat, decimals)}\u00B0 $dir"
    }

    private fun formatLngLabel(lng: Double, decimals: Int): String {
        val dir = if (lng >= 0) "E" else "W"
        return "${formatCoord(lng, decimals)}\u00B0 $dir"
    }

    private fun formatUtmValue(value: Double, spacing: Double): String {
        val km = value / 1000.0
        val decimals = when {
            spacing >= 1000 -> 0
            else -> 1
        }
        return formatCoord(km, decimals)
    }

    private fun formatCoord(value: Double, decimals: Int): String {
        val v = abs(value)
        if (decimals == 0) return v.roundToInt().toString()
        val factor = 10.0.pow(decimals).toInt()
        val scaled = (v * factor).roundToInt()
        val intPart = scaled / factor
        val fracPart = (scaled % factor).toString().padStart(decimals, '0')
        return "$intPart.$fracPart"
    }

    private fun coordDecimals(spacing: Double): Int = when {
        spacing >= 1.0 -> 0
        spacing >= 0.1 -> 1
        spacing >= 0.01 -> 2
        spacing >= 0.001 -> 3
        else -> 4
    }

    // --- Helpers ---

    private fun utmZone(longitude: Double): Int =
        (floor((longitude + 180.0) / 6.0).toInt() + 1).coerceIn(1, 60)

    private class Features {
        private val sb = StringBuilder()
        private var first = true

        fun addLine(coordsArray: String) {
            append("""{"type":"Feature","geometry":{"type":"LineString","coordinates":$coordsArray},"properties":{}}""")
        }

        fun addZoneLine(coordsArray: String) {
            append("""{"type":"Feature","geometry":{"type":"LineString","coordinates":$coordsArray},"properties":{"zone":true}}""")
        }

        fun addLabel(lng: Double, lat: Double, label: String, anchor: String) {
            append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{"label":"$label","anchor":"$anchor"}}""")
        }

        private fun append(json: String) {
            if (!first) sb.append(",")
            first = false
            sb.append(json)
        }

        fun toGeoJson(): String = """{"type":"FeatureCollection","features":[$sb]}"""
    }
}
