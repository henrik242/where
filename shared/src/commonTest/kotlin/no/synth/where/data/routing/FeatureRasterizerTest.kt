package no.synth.where.data.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureRasterizerTest {
    private fun grid(w: Int = 20, h: Int = 20) =
        TerrainGrid(w, h, 13, 0, 0, 10.0, FloatArray(w * h))

    @Test
    fun diagonalLineIsFourConnected() {
        val g = grid()
        FeatureRasterizer.drawLine(g, doubleArrayOf(1.5, 1.5, 10.5, 10.5), CellFlags.STREAM)
        for (i in 1..9) {
            assertTrue(g.hasFlag(g.index(i, i), CellFlags.STREAM))
            // Each diagonal step must mark a corner so the line has no diagonal gaps.
            assertTrue(g.hasFlag(g.index(i + 1, i), CellFlags.STREAM) || g.hasFlag(g.index(i, i + 1), CellFlags.STREAM))
        }
    }

    @Test
    fun polygonWithHoleLeavesIslandOpen() {
        val g = grid()
        val outer = doubleArrayOf(2.0, 2.0, 18.0, 2.0, 18.0, 18.0, 2.0, 18.0, 2.0, 2.0)
        val hole = doubleArrayOf(8.0, 8.0, 12.0, 8.0, 12.0, 12.0, 8.0, 12.0, 8.0, 8.0)
        FeatureRasterizer.fillPolygon(g, listOf(outer, hole), CellFlags.WATER)
        assertTrue(g.hasFlag(g.index(4, 4), CellFlags.WATER))
        assertFalse(g.hasFlag(g.index(10, 10), CellFlags.WATER))
        assertFalse(g.hasFlag(g.index(0, 0), CellFlags.WATER))
        assertFalse(g.hasFlag(g.index(19, 19), CellFlags.WATER))
    }

    @Test
    fun thinPolygonStillMarksCells() {
        // Rows 30.6 to 31.0: no cell centre lies inside, only the outline marks it.
        val g = TerrainGrid(64, 64, 13, 512, 512, 10.0, FloatArray(64 * 64))
        val channel = MvtDecoder.Feature(
            MvtDecoder.GeomType.POLYGON,
            mapOf("class" to "lake"),
            listOf(intArrayOf(0, 980, 4096, 980, 4096, 992, 0, 992, 0, 980)),
        )
        FeatureRasterizer.burnTile(g, listOf(MvtDecoder.Layer("water", 4096, listOf(channel))), 14, 4, 4)
        assertTrue(g.hasFlag(g.index(20, 30), CellFlags.WATER))
    }

    @Test
    fun mapsOpenMapTilesClasses() {
        fun f(vararg tags: Pair<String, Any>) =
            MvtDecoder.Feature(MvtDecoder.GeomType.LINESTRING, mapOf(*tags), emptyList())
        assertEquals(CellFlags.WATER, FeatureRasterizer.flagFor("water", f("class" to "lake")))
        assertEquals(CellFlags.RIVER, FeatureRasterizer.flagFor("water", f("class" to "river")))
        assertEquals(CellFlags.RIVER, FeatureRasterizer.flagFor("waterway", f("class" to "river")))
        assertEquals(CellFlags.STREAM, FeatureRasterizer.flagFor("waterway", f("class" to "stream")))
        assertNull(FeatureRasterizer.flagFor("waterway", f("class" to "stream", "brunnel" to "tunnel")))
        assertEquals(
            CellFlags.BRIDGE or CellFlags.PATH,
            FeatureRasterizer.flagFor("transportation", f("class" to "path", "brunnel" to "bridge")),
        )
        assertNull(FeatureRasterizer.flagFor("transportation", f("class" to "rail")))
        assertEquals(CellFlags.WETLAND, FeatureRasterizer.flagFor("landcover", f("class" to "wetland")))
        assertEquals(CellFlags.GLACIER, FeatureRasterizer.flagFor("landcover", f("class" to "ice")))
        assertNull(FeatureRasterizer.flagFor("landcover", f("class" to "wood")))
    }

    @Test
    fun burnsTileIntoGridCoordinates() {
        // A z14 tile covers 128x128 z13 pixels; tile (4,4) starts at z13 pixel (512,512).
        val g = TerrainGrid(64, 64, 13, 512, 512, 10.0, FloatArray(64 * 64))
        val line = MvtDecoder.Feature(
            MvtDecoder.GeomType.LINESTRING,
            mapOf("class" to "stream"),
            listOf(intArrayOf(0, 1024, 4096, 1024)),
        )
        FeatureRasterizer.burnTile(g, listOf(MvtDecoder.Layer("waterway", 4096, listOf(line))), 14, 4, 4)
        // y = 1024/4096 * 128 = 32
        assertTrue(g.hasFlag(g.index(10, 32), CellFlags.STREAM))
        assertFalse(g.hasFlag(g.index(10, 31), CellFlags.STREAM))
    }
}
