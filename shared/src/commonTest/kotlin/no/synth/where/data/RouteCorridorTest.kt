package no.synth.where.data

import no.synth.where.data.geo.LatLng
import no.synth.where.ui.map.MapLayer
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RouteCorridorTest {
    // An L-shaped route in Rana: 1 km north, then 1 km east.
    private val corner = LatLng(66.50, 14.30)
    private val route = (0..100).map { LatLng(66.49 + it * 0.0001, 14.30) } +
        (1..100).map { LatLng(66.50, 14.30 + it * 0.01 / 4.5) }

    private fun offset(p: LatLng, northMeters: Double, eastMeters: Double) = LatLng(
        p.latitude + northMeters / 111_320.0,
        p.longitude + eastMeters / (111_320.0 * cos(p.latitude * PI / 180)),
    )

    private fun inside(ring: List<Position>, p: LatLng): Boolean {
        var hit = false
        for (i in 0 until ring.size - 1) {
            val a = ring[i]
            val b = ring[i + 1]
            if ((a.latitude > p.latitude) != (b.latitude > p.latitude) &&
                p.longitude < a.longitude + (p.latitude - a.latitude) / (b.latitude - a.latitude) * (b.longitude - a.longitude)
            ) {
                hit = !hit
            }
        }
        return hit
    }

    @Test
    fun stripCoversBothSidesAndTheOuterBend() {
        val item = assertNotNull(RouteCorridor.download(route, "Test", "mapant"))
        val shape = RouteCorridor.shape(assertNotNull(item.corridor))
        fun covered(p: LatLng) = shape.coordinates.any { inside(it.first(), p) }
        assertTrue(covered(offset(route[50], 0.0, 140.0)))
        assertTrue(covered(offset(route[50], 0.0, -140.0)))
        // Outside corner of the bend, diagonally 140 m out.
        assertTrue(covered(offset(corner, 99.0, -99.0)))
        assertFalse(covered(offset(route[50], 0.0, 400.0)))
    }

    @Test
    fun downloadIsAStripOfTheLayer() {
        val item = assertNotNull(RouteCorridor.download(route, "Test", "mapant"))
        assertEquals("mapant", item.layerId)
        assertEquals("Test", item.label)
        assertEquals(RouteCorridor.MAX_ZOOM, item.maxZoom)
        assertFalse(item.downloadDem)
        assertNull(RouteCorridor.download(route.take(1), "Test", "mapant"))
        assertNull(RouteCorridor.download(route, "Test", "nope"))
        assertNull(RouteCorridor.download(List(5) { corner }, "Test", "mapant"))
    }

    @Test
    fun shortLoopKeepsAnArea() {
        // 40 m out and back: simplifies to the start twice.
        val loop = listOf(corner, offset(corner, 40.0, 0.0), corner)
        val item = assertNotNull(RouteCorridor.download(loop, "Test", "mapant"))
        val shape = RouteCorridor.shape(assertNotNull(item.corridor))
        assertTrue(shape.coordinates.any { inside(it.first(), offset(corner, 100.0, 100.0)) })
    }

    @Test
    fun downloadsAddMapAntInNorwayOnly() {
        assertEquals(listOf("kartverket", "mapant"), RouteCorridor.downloads(route, "Test", "kartverket").map { it.layerId })
        assertEquals(listOf("mapant"), RouteCorridor.downloads(route, "Test", "mapant").map { it.layerId })
        val alps = route.map { LatLng(it.latitude - 20.5, it.longitude - 7) }
        assertEquals(listOf("osm"), RouteCorridor.downloads(alps, "Test", "osm").map { it.layerId })
    }

    @Test
    fun downloadsAddHillshadeInNorwayWhenShown() {
        assertEquals(listOf("kartverket", "mapant", "hillshade"), RouteCorridor.downloads(route, "Test", "kartverket", withHillshade = true).map { it.layerId })
        val alps = route.map { LatLng(it.latitude - 20.5, it.longitude - 7) }
        assertEquals(listOf("osm"), RouteCorridor.downloads(alps, "Test", "osm", withHillshade = true).map { it.layerId })
    }

    @Test
    fun everyMapLayerHasADownloadLayer() {
        for (layer in MapLayer.entries) assertNotNull(DownloadLayers.all.find { it.id == layer.downloadId }, layer.name)
    }

    @Test
    fun routeDownloadIsAShapeAndRegionsAreBoxes() {
        val corridor = assertNotNull(RouteCorridor.download(route, "Test", "mapant"))
        val shape = assertIs<OfflinePackDefinition.Shape>(packDefinition(corridor, "file://style.json", 2f))
        assertEquals(16.0, shape.maxZoom)
        val region = corridor.copy(corridor = null)
        assertIs<OfflinePackDefinition.TilePyramid>(packDefinition(region, "file://style.json", 2f))
    }
}
