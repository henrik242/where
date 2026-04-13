package no.synth.where.data

import no.synth.where.data.geo.LatLngBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileUtilsTest {

    @Test
    fun returnsAtLeastOne() {
        val bounds = LatLngBounds(south = 59.0, west = 10.0, north = 59.01, east = 10.01)
        val count = TileUtils.estimateTileCount(bounds, 0, 0)
        assertTrue(count >= 1)
    }

    @Test
    fun higherZoomProducesMoreTiles() {
        val bounds = LatLngBounds(south = 59.0, west = 10.0, north = 61.0, east = 12.0)
        val lowZoom = TileUtils.estimateTileCount(bounds, 5, 8)
        val highZoom = TileUtils.estimateTileCount(bounds, 5, 12)
        assertTrue(highZoom > lowZoom, "Higher max zoom should produce more tiles")
    }

    @Test
    fun largerRegionProducesMoreTiles() {
        val small = LatLngBounds(south = 59.0, west = 10.0, north = 60.0, east = 11.0)
        val large = LatLngBounds(south = 58.0, west = 9.0, north = 62.0, east = 13.0)
        val smallCount = TileUtils.estimateTileCount(small, 5, 10)
        val largeCount = TileUtils.estimateTileCount(large, 5, 10)
        assertTrue(largeCount > smallCount, "Larger region should produce more tiles")
    }

    @Test
    fun latLngToTileCoordKnownValues() {
        // At zoom 0, the entire world is one tile
        val coord = TileUtils.latLngToTileCoord(0.0, 0.0, 0)
        assertEquals(0, coord.z)
        assertEquals(0, coord.x)
        assertEquals(0, coord.y)
    }

    @Test
    fun latLngToTileCoordPixelRange() {
        val coord = TileUtils.latLngToTileCoord(63.43, 10.39, 15)
        assertEquals(15, coord.z)
        assertTrue(coord.pixelX in 0..255, "pixelX should be in tile range")
        assertTrue(coord.pixelY in 0..255, "pixelY should be in tile range")
    }

    @Test
    fun tileToLatLngRoundTrip() {
        val lat = 63.43
        val lng = 10.39
        val zoom = 14
        val coord = TileUtils.latLngToTileCoord(lat, lng, zoom)
        val back = TileUtils.tileToLatLng(coord.z, coord.x, coord.y)
        // Tile corner should be close to original point (within one tile's span)
        assertTrue(kotlin.math.abs(back.latitude - lat) < 0.1, "Latitude round trip should be close")
        assertTrue(kotlin.math.abs(back.longitude - lng) < 0.1, "Longitude round trip should be close")
    }
}
