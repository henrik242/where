package no.synth.where.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.synth.where.ui.EMPTY_HEX_GEOJSON
import no.synth.where.ui.buildHexMapStyle
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.NveOverlay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadLayersTest {

    @Test
    fun offersExactlyTheSupportedLayers() {
        assertEquals(
            listOf(
                "kartverket", "toporaster", "sjokartraster", "mapant", "satellite",
                "osm", "opentopomap", "waymarkedtrails", DownloadLayers.OSM_PATHS_ID,
                "avalanchezones", "steepness",
            ),
            DownloadLayers.all.map { it.id },
        )
    }

    @Test
    fun nveLayersMirrorTheOverlayEnum() {
        NveOverlay.entries.forEach { overlay ->
            val layer = assertNotNull(
                DownloadLayers.all.find { it.id == overlay.sourceId },
                "${overlay.name} should be downloadable"
            )
            assertEquals(overlay.tileUrl, layer.tileUrl)
            assertEquals(16, layer.maxZoom, "NVE caches nothing past z16")
            assertTrue(layer.isOverlay)
        }
    }

    /**
     * The pack is only usable if it stores tiles under the URLs the live map asks for, so the
     * vector overlay's download style has to be the live style rather than a hand-built copy.
     */
    @Test
    fun osmPathsDownloadStyleMatchesTheLiveOverlayStyle() {
        val json = DownloadLayers.getDownloadStyleJson(DownloadLayers.OSM_PATHS_ID)
        assertEquals(
            MapStyle.getStyle(selectedLayer = MapLayer.OSM, showOsmPaths = true),
            json,
        )
        assertTrue(json.contains("\"type\": \"vector\""), "Should download the vector source")
        assertTrue(json.contains("tile.openstreetmap.org"), "Overlays download over an OSM base")
    }

    /** Both styles are concatenated by hand, so check every layer yields resolvable JSON. */
    @Test
    fun everyLayerBuildsParseableDownloadAndPreviewStyles() {
        for (layer in DownloadLayers.all) {
            for ((what, json) in listOf(
                "download" to DownloadLayers.getDownloadStyleJson(layer.id),
                "preview" to buildHexMapStyle(layer.id, EMPTY_HEX_GEOJSON),
            )) {
                val style = Json.parseToJsonElement(json).jsonObject
                val declared = style.getValue("sources").jsonObject.keys
                val referenced = style.getValue("layers").jsonArray
                    .mapNotNull { it.jsonObject["source"]?.jsonPrimitive?.content }
                    .toSet()
                assertTrue(
                    declared.containsAll(referenced),
                    "${layer.id} $what style references undeclared ${referenced - declared}"
                )
            }
        }
    }

    @Test
    fun osmPathsHexPreviewSkipsTheVectorSourceItCannotDrawAsRaster() {
        val style = buildHexMapStyle(DownloadLayers.OSM_PATHS_ID, EMPTY_HEX_GEOJSON)
        assertFalse(
            style.contains(MapStyle.OSM_PATHS_TILEJSON_URL),
            "TileJSON URL is not an {z}/{x}/{y} template and must not be used as raster tiles"
        )
        assertTrue(style.contains("\"hexgrid\""), "Grid should still be drawn")
        assertTrue(style.contains("tile.openstreetmap.org"), "OSM base should still be drawn")
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
        // Requesting exactly the layer max is the boundary, and passes through unclamped.
        assertEquals(16, DownloadLayers.effectiveMaxZoom("mapant", 16))
    }

    @Test
    fun effectiveMaxZoomFallsBackForUnknownLayer() {
        // Unknown layer falls back to the generic z18 ceiling, so the request passes through.
        assertEquals(16, DownloadLayers.effectiveMaxZoom("nonexistent", 16))
    }
}
