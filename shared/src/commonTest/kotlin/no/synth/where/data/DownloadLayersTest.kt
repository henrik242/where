package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadLayersTest {

    @Test
    fun hasTenLayers() {
        assertEquals(10, DownloadLayers.all.size)
    }

    @Test
    fun satelliteLayerIsCappedAtZoom14() {
        val satellite = assertNotNull(DownloadLayers.all.find { it.id == "satellite" }, "Satellite layer should exist")
        assertEquals(14, satellite.maxZoom, "Sentinel-2 has no detail past its native ~10 m resolution")
    }

    @Test
    fun mapAntLayerIsCappedAtZoom16() {
        val mapant = assertNotNull(DownloadLayers.all.find { it.id == "mapant" }, "MapAnt layer should exist")
        assertEquals(16, mapant.maxZoom, "MapAnt serves tiles only through z16; z17+ returns 404")
    }

    @Test
    fun openTopoMapLayerIsCappedAtZoom17() {
        val opentopo = assertNotNull(DownloadLayers.all.find { it.id == "opentopomap" }, "OpenTopoMap layer should exist")
        assertEquals(17, opentopo.maxZoom, "OpenTopoMap serves tiles only through z17; z18+ returns 404")
    }

    @Test
    fun allIdsAreUnique() {
        val ids = DownloadLayers.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "Layer IDs should be unique")
    }

    @Test
    fun knownLayerReturnsCorrectUrl() {
        val url = DownloadLayers.tileUrlForLayer("kartverket")
        assertTrue(url.contains("kartverket"), "Kartverket URL should contain 'kartverket'")
        assertTrue(url.contains("{z}"), "URL should contain zoom placeholder")
    }

    @Test
    fun unknownLayerReturnsOsmFallback() {
        val url = DownloadLayers.tileUrlForLayer("nonexistent")
        assertTrue(url.contains("openstreetmap"), "Unknown layer should fall back to OSM")
    }

    @Test
    fun downloadStyleJsonContainsVersionAndSource() {
        val json = DownloadLayers.getDownloadStyleJson("osm")
        assertTrue(json.contains("\"version\": 8"), "Style JSON should have version 8")
        assertTrue(json.contains("\"osm\""), "Style JSON should reference the layer name as source")
        assertTrue(json.contains("openstreetmap"), "OSM style should reference OSM tile URL")
    }

    @Test
    fun effectiveMaxZoomPassesThroughWhenBelowLayerMax() {
        // Kartverket supports z18, so a z14 request is used as-is.
        assertEquals(14, DownloadLayers.effectiveMaxZoom("kartverket", 14))
    }

    @Test
    fun effectiveMaxZoomClampsToLayerMax() {
        // Satellite tops out at z14, so a z16 request is clamped down.
        assertEquals(14, DownloadLayers.effectiveMaxZoom("satellite", 16))
        // Terrain tops out at z15.
        assertEquals(15, DownloadLayers.effectiveMaxZoom("terrain", 16))
    }

    @Test
    fun effectiveMaxZoomFallsBackForUnknownLayer() {
        // Unknown layer falls back to the generic z18 ceiling, so the request passes through.
        assertEquals(16, DownloadLayers.effectiveMaxZoom("nonexistent", 16))
    }
}
