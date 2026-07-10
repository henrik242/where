package no.synth.where.data

import no.synth.where.ui.map.MapLayer
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapStyleTest {

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
    fun nonSatelliteBaseSourceHasNoMaxzoom() {
        val style = MapStyle.getStyle(selectedLayer = MapLayer.KARTVERKET)
        assertFalse(style.contains("maxzoom"), "Default base layers should not set maxzoom")
    }
}
