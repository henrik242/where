package no.synth.where.data

import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.NveOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The JSON from a source or layer key up to that object's closing brace, to assert inside one
 * block. Splits on the indented brace because tile URLs contain `{z}/{y}/{x}` placeholders.
 */
private fun blockAfter(style: String, key: String) = style.substringAfter(key).substringBefore("\n    }")

class MapStyleTest {

    @Test
    fun styleJsonIsWellFormed() {
        val style = MapStyle.getStyle()
        assertTrue(style.trim().startsWith("{"), "Style should start with {")
        assertTrue(style.trim().endsWith("}"), "Style should end with }")
        assertTrue(style.contains("\"version\""), "Style should contain version")
        assertTrue(style.contains("\"sources\""), "Style should contain sources")
        assertTrue(style.contains("\"layers\""), "Style should contain layers")
        assertTrue(style.contains("\"kartverket\""), "Style should contain the kartverket source")
    }

    @Test
    fun customGlyphsUrlIsEmbedded() {
        val style = MapStyle.getStyle(glyphsUrl = "asset://fonts/{fontstack}/{range}.pbf")
        assertTrue(style.contains("\"glyphs\": \"asset://fonts/{fontstack}/{range}.pbf\""))
    }

    @Test
    fun onlySelectedBaseSourceIncluded() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.OSM)
        assertTrue(style.contains("\"osm\""), "Should contain the osm source")
        assertFalse(style.contains("\"kartverket\""))
        assertFalse(style.contains("\"toporaster\""))
        assertFalse(style.contains("\"opentopomap\""))
        assertFalse(style.contains("\"sjokartraster\""))
    }

    @Test
    fun satelliteStyleUsesEoxSourceCappedAtZoom14() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.SATELLITE)
        assertTrue(style.contains("s2cloudless"), "Should use the EOX Sentinel-2 cloudless tiles")
        assertTrue(style.contains("\"maxzoom\": 14"), "Satellite source should be capped at zoom 14")
    }

    @Test
    fun mapAntStyleIsCappedAtZoom16() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.MAPANT)
        assertTrue(style.contains("mapant.no"), "Should use the MapAnt tiles")
        assertTrue(style.contains("\"maxzoom\": 16"), "MapAnt source should be capped at zoom 16 (z17+ returns 404)")
    }

    @Test
    fun openTopoMapStyleIsCappedAtZoom17() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.OPENTOPOMAP)
        assertTrue(style.contains("opentopomap.org"), "Should use the OpenTopoMap tiles")
        assertTrue(style.contains("\"maxzoom\": 17"), "OpenTopoMap source should be capped at zoom 17 (z18+ returns 404)")
    }

    @Test
    fun nonSatelliteBaseSourceHasNoMaxzoom() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.KARTVERKET)
        assertFalse(style.contains("maxzoom"), "Default base layers should not set maxzoom")
    }

    @Test
    fun waymarkedTrailsIncludedWhenEnabled() {
        assertTrue(MapStyle.getStyle(showWaymarkedTrails = true).contains("\"waymarkedtrails\""))
        assertFalse(MapStyle.getStyle(showWaymarkedTrails = false).contains("\"waymarkedtrails\""))
    }

    @Test
    fun steepnessOverlayUsesTheSteepnessOnlyService() {
        val style = MapStyle.getStyle(nveOverlay = NveOverlay.STEEPNESS)
        assertTrue(style.contains("\"steepness\""), "Should contain the steepness source")
        assertTrue(style.contains("\"steepness-layer\""), "Should contain the steepness layer")
        assertTrue(style.contains("Bratthet_2024"), "Should use the steepness-only NVE service")
        assertFalse(style.contains("Bratthet_med_utlop_2024"), "Should not pull in runout zones")
    }

    @Test
    fun runoutOverlayUsesTheRunoutService() {
        val style = MapStyle.getStyle(nveOverlay = NveOverlay.STEEPNESS_RUNOUT)
        assertTrue(style.contains("Bratthet_med_utlop_2024"), "Should use the NVE runout service")
        assertTrue(style.contains("\"avalanchezones-layer\""), "Source id stays legacy for offline downloads")
        assertFalse(style.contains("\"steepness\""), "Only one NVE overlay can be in a style")
    }

    @Test
    fun noNveOverlayByDefault() {
        val style = MapStyle.getStyle()
        assertFalse(style.contains("gis3.nve.no"))
    }

    @Test
    fun nveOverlaysAreCappedAtZoom16() {
        // NVE caches nothing past z16, so MapLibre must overzoom rather than fetch 404s.
        for (overlay in NveOverlay.entries) {
            val source = blockAfter(MapStyle.getStyle(nveOverlay = overlay), "\"${overlay.sourceId}\": {")
            assertTrue(source.contains("\"maxzoom\": 16"), "${overlay.name} source should cap at zoom 16")
            assertTrue(source.contains("\"minzoom\": 6"), "${overlay.name} source should start at zoom 6")
        }
    }

    @Test
    fun nveOverlayIsDrawnAtSixtyPercentOpacity() {
        for (overlay in NveOverlay.entries) {
            val layer = blockAfter(MapStyle.getStyle(nveOverlay = overlay), "\"${overlay.sourceId}-layer\"")
            assertTrue(layer.contains("\"raster-opacity\": 0.6"), "${overlay.name} layer should be 60% opaque")
        }
    }

    @Test
    fun overlaysAreDrawnBaseNveTrails() {
        val style = MapStyle.getStyle(
            showWaymarkedTrails = true,
            nveOverlay = NveOverlay.STEEPNESS,
        )
        val order = listOf("base-layer", "steepness-layer", "waymarkedtrails-layer")
            .map { style.indexOf(it) }
        assertFalse(order.contains(-1), "Every overlay should be present")
        assertEquals(order.sorted(), order, "Overlays should be drawn base, NVE, then trails")
    }
}
