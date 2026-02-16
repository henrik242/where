package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadLayersTest {

    @Test
    fun hasSixLayers() {
        assertEquals(6, DownloadLayers.all.size)
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
}
