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
            CoordFormat.LATLNG -> buildLatLngGrid(centerLat, centerLng, zoom, dms = false)
            CoordFormat.DMS -> buildLatLngGrid(centerLat, centerLng, zoom, dms = true)
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

    /**
     * Approximates the actual on-screen viewport in degrees. A typical
     * portrait phone shows roughly 1.4 tiles wide and 3 tiles tall at any
     * given zoom, so the vertical multiplier is about double the horizontal
     * one. Used to decide whether a UTM cell fits entirely on screen.
     */
    private fun viewportBounds(centerLat: Double, centerLng: Double, zoom: Double): LatLngBounds {
        val tileSpan = 360.0 / 2.0.pow(zoom)
        val halfSpanLng = tileSpan * 0.7
        val halfSpanLat = tileSpan * 1.5
        return LatLngBounds(
            south = (centerLat - halfSpanLat).coerceIn(-80.0, 84.0),
            north = (centerLat + halfSpanLat).coerceIn(-80.0, 84.0),
            west = (centerLng - halfSpanLng).coerceAtLeast(-180.0),
            east = (centerLng + halfSpanLng).coerceAtMost(180.0)
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
        zoom < 8 -> -1.0
        zoom < 10 -> 10000.0
        else -> 1000.0
    }

    // --- Lat/Lng grid (decimal degrees or DMS) ---

    private fun buildLatLngGrid(centerLat: Double, centerLng: Double, zoom: Double, dms: Boolean): String {
        val bounds = lineBounds(centerLat, centerLng, zoom)
        val spacing = if (dms) dmsGridSpacing(zoom) else latLngGridSpacing(zoom)
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
            val text = if (dms) formatDmsLatLabel(lat, spacing) else formatLatLabel(lat, decimals)
            f.addLabel(labelLng, lat, text, "right")
            i++
        }

        val startLng = floor(bounds.west / spacing) * spacing
        i = 0
        while (startLng + i * spacing <= bounds.east) {
            val lng = startLng + i * spacing
            f.addLine("[[$lng,${bounds.south}],[$lng,${bounds.north}]]")
            val text = if (dms) formatDmsLngLabel(lng, spacing) else formatLngLabel(lng, decimals)
            f.addLabel(lng, labelLat, text, "top")
            i++
        }

        return f.toGeoJson()
    }

    /**
     * Grid spacing in decimal degrees, snapped to clean DMS values (degrees,
     * arc-minutes, arc-seconds) so labels read e.g. `30'`, `1°`, `10"`.
     */
    private fun dmsGridSpacing(zoom: Double): Double = when {
        zoom < 3 -> 30.0                    // 30°
        zoom < 5 -> 10.0                    // 10°
        zoom < 6 -> 5.0                     //  5°
        zoom < 7 -> 1.0                     //  1°
        zoom < 9 -> 30.0 / 60.0             // 30'
        zoom < 10 -> 10.0 / 60.0            // 10'
        zoom < 12 -> 1.0 / 60.0             //  1'
        zoom < 14 -> 30.0 / 3600.0          // 30"
        zoom < 16 -> 10.0 / 3600.0          // 10"
        zoom < 18 -> 5.0 / 3600.0           //  5"
        else -> 1.0 / 3600.0                //  1"
    }

    // --- UTM/MGRS grid ---

    private fun buildUtmGrid(centerLat: Double, centerLng: Double, zoom: Double): String {
        val spacing = utmGridSpacing(zoom)
        val f = Features()
        val viewBounds = lineBounds(centerLat, centerLng, zoom)

        val labelBounds = viewportBounds(centerLat, centerLng, zoom)
        if (spacing > 0) {
            // Close zoom: curved per-zone UTM grid only — no latlng-aligned overlay.
            val bounds = utmGridBounds(centerLat, centerLng, spacing)
            val utm = CoordinateFormatter.latLngToUtm(centerLat, centerLng)
            val zone = utm.zone
            val range = zoneRange(zone, utm.letter) ?: standardZoneRange(zone)
            val off = labelOffset(zoom)

            if (bounds.south < 0 && bounds.north > 0) {
                val southBounds = LatLngBounds(bounds.south, bounds.west, 0.0, bounds.east)
                val northBounds = LatLngBounds(0.0, bounds.west, bounds.north, bounds.east)
                appendUtmZoneGrid(f, southBounds, spacing, false, zone, centerLat, centerLng, off,
                    zoneWest = range.first, zoneEast = range.second)
                appendUtmZoneGrid(f, northBounds, spacing, true, zone, centerLat, centerLng, off,
                    zoneWest = range.first, zoneEast = range.second)
            } else {
                appendUtmZoneGrid(f, bounds, spacing, centerLat >= 0, zone, centerLat, centerLng, off,
                    zoneWest = range.first, zoneEast = range.second)
            }
        } else {
            // Global view: MGRS zone/band borders.
            // At zoom < 3 the inner per-zone UTM grid is skipped — world-scale
            // navigation isn't done in UTM, and the density (60 zones × many
            // lines) would be unreadable.
            if (zoom >= 3.0) {
                val coarseSpacing = globalUtmSpacing(zoom)
                forEachVisibleCell(viewBounds) { cell ->
                    val cellBounds = LatLngBounds(
                        south = maxOf(cell.south, viewBounds.south),
                        west = maxOf(cell.west, viewBounds.west),
                        north = minOf(cell.north, viewBounds.north),
                        east = minOf(cell.east, viewBounds.east)
                    )
                    val northern = cell.south >= 0
                    appendUtmZoneGrid(f, cellBounds, coarseSpacing, northern, cell.zone,
                        0.0, 0.0, 0.0, drawLabels = false,
                        zoneWest = cell.west, zoneEast = cell.east)
                }
            }
            appendZoneBorders(f, viewBounds)
        }
        // MGRS designators (e.g. "32V") in each visible cell — at all zooms.
        appendCellLabels(f, labelBounds)

        return f.toGeoJson()
    }

    private fun globalUtmSpacing(zoom: Double): Double = when {
        zoom < 3 -> 1_000_000.0  // 1000 km
        zoom < 5 -> 500_000.0    //  500 km
        zoom < 7 -> 200_000.0    //  200 km
        else -> 100_000.0        //  100 km
    }

    private data class Band(val letter: Char, val south: Double, val north: Double)

    // UTM latitude bands C–X, each 8° tall except X which spans 72°N–84°N.
    // Letters I and O are skipped.
    private val LAT_BANDS: List<Band> = listOf(
        Band('C', -80.0, -72.0), Band('D', -72.0, -64.0),
        Band('E', -64.0, -56.0), Band('F', -56.0, -48.0),
        Band('G', -48.0, -40.0), Band('H', -40.0, -32.0),
        Band('J', -32.0, -24.0), Band('K', -24.0, -16.0),
        Band('L', -16.0, -8.0), Band('M', -8.0, 0.0),
        Band('N', 0.0, 8.0), Band('P', 8.0, 16.0),
        Band('Q', 16.0, 24.0), Band('R', 24.0, 32.0),
        Band('S', 32.0, 40.0), Band('T', 40.0, 48.0),
        Band('U', 48.0, 56.0), Band('V', 56.0, 64.0),
        Band('W', 64.0, 72.0), Band('X', 72.0, 84.0)
    )

    /**
     * Longitude range `(west, east)` of a UTM zone in a given band, accounting
     * for the Norway (32V widened westward) and Svalbard (31X/33X/35X/37X
     * widened; 32X/34X/36X absent) exceptions. Returns null if the zone does
     * not exist in that band.
     */
    private fun zoneRange(zone: Int, bandLetter: Char): Pair<Double, Double>? {
        if (zone < 1 || zone > 60) return null
        if (bandLetter == 'V') {
            if (zone == 31) return 0.0 to 3.0
            if (zone == 32) return 3.0 to 12.0
        }
        if (bandLetter == 'X') {
            return when (zone) {
                31 -> 0.0 to 9.0
                32, 34, 36 -> null
                33 -> 9.0 to 21.0
                35 -> 21.0 to 33.0
                37 -> 33.0 to 42.0
                else -> standardZoneRange(zone)
            }
        }
        return standardZoneRange(zone)
    }

    private fun standardZoneRange(zone: Int): Pair<Double, Double> {
        val west = (zone - 1) * 6.0 - 180.0
        return west to (west + 6.0)
    }

    private class Cell(
        val zone: Int,
        val bandLetter: Char,
        val west: Double,
        val east: Double,
        val south: Double,
        val north: Double
    )

    /** Iterates every (zone × band) cell that overlaps [bounds], honouring UTM exceptions. */
    private inline fun forEachVisibleCell(bounds: LatLngBounds, block: (Cell) -> Unit) {
        for (band in LAT_BANDS) {
            if (band.north <= bounds.south || band.south >= bounds.north) continue
            for (zone in 1..60) {
                val range = zoneRange(zone, band.letter) ?: continue
                if (range.second <= bounds.west || range.first >= bounds.east) continue
                block(Cell(zone, band.letter, range.first, range.second, band.south, band.north))
            }
        }
    }

    /**
     * Straight MGRS zone/band borders: meridians at each zone boundary
     * (honouring 32V / Svalbard exceptions) and parallels every 8°
     * (band boundaries, with X band extending to 84°N).
     */
    private fun appendZoneBorders(f: Features, bounds: LatLngBounds) {
        // Meridians: per band, since zone boundaries differ between bands (32V, 31X-37X).
        for (band in LAT_BANDS) {
            val visSouth = maxOf(band.south, bounds.south)
            val visNorth = minOf(band.north, bounds.north)
            if (visSouth >= visNorth) continue
            val meridians = mutableSetOf<Double>()
            for (zone in 1..60) {
                val r = zoneRange(zone, band.letter) ?: continue
                if (r.first in bounds.west..bounds.east) meridians.add(r.first)
                if (r.second in bounds.west..bounds.east) meridians.add(r.second)
            }
            for (lng in meridians) {
                f.addZoneLine("[[$lng,$visSouth],[$lng,$visNorth]]")
            }
        }
        // Band parallels (each band's southern boundary, plus the X band's northern cap).
        for (band in LAT_BANDS) {
            if (band.south in bounds.south..bounds.north) {
                f.addZoneLine("[[${bounds.west},${band.south}],[${bounds.east},${band.south}]]")
            }
        }
        if (84.0 in bounds.south..bounds.north) {
            f.addZoneLine("[[${bounds.west},84.0],[${bounds.east},84.0]]")
        }
    }

    /**
     * Combined MGRS designator label (e.g. "32V") per visible (zone × band)
     * cell. If the entire cell fits in the viewport the label sits at the
     * cell centre; otherwise (close zoom, viewport inside the cell) it sits
     * at the top-centre of the visible portion so it doesn't collide with
     * the crosshair. MapLibre culls collisions so dense low-zoom views show
     * a readable subset.
     */
    private fun appendCellLabels(f: Features, bounds: LatLngBounds) {
        forEachVisibleCell(bounds) { cell ->
            val labelLng = (maxOf(cell.west, bounds.west) + minOf(cell.east, bounds.east)) / 2.0
            val text = "${cell.zone}${cell.bandLetter}"
            val fullyVisible = cell.south >= bounds.south && cell.north <= bounds.north &&
                cell.west >= bounds.west && cell.east <= bounds.east
            if (fullyVisible) {
                f.addCellLabel(labelLng, (cell.south + cell.north) / 2.0, text, "center")
            } else {
                f.addCellLabel(labelLng, minOf(cell.north, bounds.north), text, "top")
            }
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
        labelOffset: Double,
        drawLabels: Boolean = true,
        zoneWest: Double = standardZoneRange(zone).first,
        zoneEast: Double = standardZoneRange(zone).second
    ) {
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
            val coords = buildLineCoords(numSamples, zoneWest, zoneEast) { i ->
                val n = minNorthing + (maxNorthing - minNorthing) * i / numSamples
                CoordinateFormatter.utmToLatLng(zone, e, n, northern)
            } ?: continue
            f.addLine("[$coords]")
            if (drawLabels) {
                val lp = CoordinateFormatter.utmToLatLng(zone, e, labelUtm.northing, northern)
                f.addLabel(lp.longitude, lp.latitude, formatUtmValue(e, spacing), "top")
            }
        }

        // Constant northing lines (roughly east-west)
        val startN = floor(minNorthing / spacing) * spacing
        idx = 0
        while (true) {
            val n = startN + idx * spacing
            if (n > maxNorthing) break
            idx++
            val coords = buildLineCoords(numSamples, zoneWest, zoneEast) { i ->
                val eVal = minEasting + (maxEasting - minEasting) * i / numSamples
                CoordinateFormatter.utmToLatLng(zone, eVal, n, northern)
            } ?: continue
            f.addLine("[$coords]")
            if (drawLabels) {
                val lp = CoordinateFormatter.utmToLatLng(zone, labelUtm.easting, n, northern)
                f.addLabel(lp.longitude, lp.latitude, formatUtmValue(n, spacing), "right")
            }
        }
    }

    // --- Line sampling ---

    private inline fun buildLineCoords(
        numSamples: Int,
        minLng: Double = -180.0,
        maxLng: Double = 180.0,
        pointAt: (Int) -> LatLng
    ): String? {
        val sb = StringBuilder()
        var pointCount = 0
        // Small epsilon so points right on the zone boundary aren't dropped due
        // to floating-point noise in the UTM inverse projection.
        val eps = 1e-6
        for (i in 0..numSamples) {
            val ll = pointAt(i)
            if (!ll.latitude.isFinite() || !ll.longitude.isFinite()) continue
            if (ll.latitude < -85 || ll.latitude > 85) continue
            if (ll.longitude < minLng - eps || ll.longitude > maxLng + eps) continue
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

    private fun formatDmsLatLabel(lat: Double, spacing: Double): String {
        val dir = if (lat >= 0) "N" else "S"
        return "${formatDmsValue(abs(lat), spacing)} $dir"
    }

    private fun formatDmsLngLabel(lng: Double, spacing: Double): String {
        val dir = if (lng >= 0) "E" else "W"
        return "${formatDmsValue(abs(lng), spacing)} $dir"
    }

    /**
     * Formats a non-negative coordinate at DMS-aligned [spacing] using only
     * the units relevant at that resolution: degrees if the spacing is ≥ 1°,
     * arc-minutes if ≥ 1', otherwise arc-seconds.
     */
    private fun formatDmsValue(value: Double, spacing: Double): String {
        val totalSeconds = (value * 3600.0).roundToInt()
        val d = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return when {
            spacing >= 1.0 -> "$d\u00B0"
            spacing >= 1.0 / 60.0 -> "$d\u00B0$m'"
            else -> "$d\u00B0$m'$s\""
        }
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
            if (!lng.isFinite() || !lat.isFinite()) return
            append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{"label":"$label","anchor":"$anchor"}}""")
        }

        fun addCellLabel(lng: Double, lat: Double, label: String, anchor: String) {
            if (!lng.isFinite() || !lat.isFinite()) return
            append("""{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{"label":"$label","anchor":"$anchor","cell":true}}""")
        }

        private fun append(json: String) {
            if (!first) sb.append(",")
            first = false
            sb.append(json)
        }

        fun toGeoJson(): String = """{"type":"FeatureCollection","features":[$sb]}"""
    }
}
