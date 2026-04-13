package no.synth.where.ui.map

import no.synth.where.data.geo.CoordFormat
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoordGridTest {

    // --- Basic output shape ---

    @Test
    fun outputIsAlwaysAFeatureCollectionJson() {
        val cases = listOf(
            Triple(59.9, 10.75, 14.0) to CoordFormat.LATLNG,
            Triple(59.9, 10.75, 14.0) to CoordFormat.DMS,
            Triple(59.9, 10.75, 14.0) to CoordFormat.UTM,
            Triple(59.9, 10.75, 14.0) to CoordFormat.MGRS,
            Triple(0.0, 0.0, 2.0) to CoordFormat.UTM,
            Triple(-45.0, -70.0, 5.0) to CoordFormat.UTM,
            Triple(83.5, 20.0, 3.0) to CoordFormat.UTM
        )
        for ((coords, format) in cases) {
            val (lat, lng, zoom) = coords
            val json = CoordGrid.buildGeoJson(lat, lng, zoom, format)
            assertTrue(
                json.startsWith("""{"type":"FeatureCollection","features":["""),
                "expected FeatureCollection header for $format @ $coords, got: ${json.take(80)}"
            )
            assertTrue(json.endsWith("]}"), "expected closing ]} for $format @ $coords")
        }
    }

    @Test
    fun outputNeverContainsNaNOrInfinity() {
        val cases = listOf(
            // Poles — UTM becomes undefined above 84°N / below 80°S.
            Triple(89.0, 0.0, 3.0),
            Triple(-85.0, 0.0, 3.0),
            // Exactly at UTM bounds.
            Triple(84.0, 10.0, 4.0),
            Triple(-80.0, 10.0, 4.0),
            // Antimeridian.
            Triple(0.0, 179.9, 3.0),
            Triple(0.0, -179.9, 3.0)
        )
        for ((lat, lng, zoom) in cases) {
            val json = CoordGrid.buildGeoJson(lat, lng, zoom, CoordFormat.UTM)
            assertFalse(json.contains("NaN"), "NaN at $lat,$lng z=$zoom")
            assertFalse(json.contains("Infinity"), "Infinity at $lat,$lng z=$zoom")
        }
    }

    // --- DMS grid ---

    @Test
    fun dmsGridUsesArcMinuteOrSecondLabelsAtCloseZoom() {
        // Zoom 11: spacing should be 1' (1/60°). Labels look like `59°54' N`.
        val json = CoordGrid.buildGeoJson(59.9, 10.75, 11.0, CoordFormat.DMS)
        // Look for a label containing an arc-minute marker.
        assertTrue(
            json.contains(Regex(""""label":"\d+°\d+' [NSEW]""")),
            "expected arc-minute DMS label at zoom 11"
        )
        assertFalse(json.contains("NaN"))
    }

    @Test
    fun dmsGridUsesDegreeOnlyLabelsAtLowZoom() {
        val json = CoordGrid.buildGeoJson(0.0, 0.0, 4.0, CoordFormat.DMS)
        // At low zoom the spacing is in whole degrees: `30° N`, `90° W`, etc.
        assertTrue(
            json.contains(Regex(""""label":"\d+° [NSEW]""")),
            "expected whole-degree DMS label at zoom 4"
        )
    }

    // --- UTM zone labels: standard cases ---

    @Test
    fun globalViewLabelsStandardCellInOslo() {
        // Oslo is at ~10.75°E, 59.9°N — cell 32V.
        val json = CoordGrid.buildGeoJson(59.9, 10.75, 5.0, CoordFormat.UTM)
        assertTrue(hasCellLabel(json, "32V"), "expected 32V near Oslo")
    }

    @Test
    fun globalViewLabelsStandardCellInLondon() {
        // London ~0°E, 51.5°N — cell 31U.
        val json = CoordGrid.buildGeoJson(51.5, 0.0, 5.0, CoordFormat.UTM)
        assertTrue(hasCellLabel(json, "31U"), "expected 31U near London")
    }

    // --- UTM zone labels: exceptions ---

    @Test
    fun zone32VIsWidenedWestwardInBandV() {
        // Center in the widened portion of 32V (5°E, 58°N is west of the
        // standard 6° boundary but still inside 32V).
        val json = CoordGrid.buildGeoJson(58.0, 5.0, 6.0, CoordFormat.UTM)
        assertTrue(hasCellLabel(json, "32V"), "expected 32V to cover 5°E,58°N")
        // 31V should still appear for the 0°-3° strip.
        val wide = CoordGrid.buildGeoJson(60.0, 1.5, 5.0, CoordFormat.UTM)
        assertTrue(hasCellLabel(wide, "31V"), "31V covers 0-3°E in band V")
    }

    @Test
    fun svalbardZonesUseSpecialWidths() {
        // Svalbard ~78°N. Cells 33X, 35X, 37X are widened to 12°/9°; 32X, 34X, 36X don't exist.
        // Low zoom so the whole cells fit in the viewport.
        val json = CoordGrid.buildGeoJson(78.0, 20.0, 2.0, CoordFormat.UTM)
        assertTrue(hasCellLabel(json, "33X"), "expected 33X (9°-21°E)")
        assertFalse(hasCellLabel(json, "32X"), "32X must not exist")
        assertFalse(hasCellLabel(json, "34X"), "34X must not exist")

        // 33X is widened to 9°–21° (centre 15°E) when fully in view.
        val lbl33 = cellLabelLongitude(json, "33X", minLat = 72.0, maxLat = 84.0)
        assertTrue(lbl33 != null, "expected a 33X cell label")
        assertTrue(
            lbl33 in 14.0..16.0,
            "expected 33X label centred ~15°E (widened 9°-21°), got $lbl33"
        )
        // 37X is widened to 33°–42° (centre 37.5°E); standard would be 36°–42°/39°E.
        val lbl37 = cellLabelLongitude(json, "37X", minLat = 72.0, maxLat = 84.0)
        assertTrue(lbl37 != null, "expected a 37X cell label")
        assertTrue(
            lbl37 in 37.0..38.0,
            "expected 37X label centred ~37.5°E (widened 33°-42°), got $lbl37"
        )
    }

    // --- Helpers ---

    /** True if a cell-label feature with the given combined text appears. */
    private fun hasCellLabel(geoJson: String, label: String): Boolean {
        val pattern = Regex(""""label":"${Regex.escape(label)}","anchor":"[^"]+","cell":true""")
        return pattern.containsMatchIn(geoJson)
    }

    /**
     * Returns the longitude of a cell-label feature matching [label] whose
     * latitude falls within [[minLat], [maxLat]], or null if none.
     */
    private fun cellLabelLongitude(
        geoJson: String,
        label: String,
        minLat: Double,
        maxLat: Double
    ): Double? {
        // Feature shape:
        // "coordinates":[<lng>,<lat>]},"properties":{..."label":"<X>"..."cell":true...}
        val re = Regex(
            """"coordinates":\[([-\d.]+),([-\d.]+)]},"properties":\{[^}]*"label":"${Regex.escape(label)}"[^}]*"cell":true"""
        )
        for (m in re.findAll(geoJson)) {
            val lng = m.groupValues[1].toDouble()
            val lat = m.groupValues[2].toDouble()
            if (lat in minLat..maxLat) return lng
        }
        return null
    }
}
