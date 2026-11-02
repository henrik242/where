package no.synth.where.data.routing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import no.synth.where.data.TerrainClient
import no.synth.where.data.TileUtils
import no.synth.where.data.geo.LatLng
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CliffMapTest {
    private val originalClient = TerrainClient.client

    @AfterTest
    fun restoreClient() {
        TerrainClient.client = originalClient
    }

    /** Serves [body] with [status] for every terrain request. */
    private fun serve(status: HttpStatusCode, body: ByteArray = ByteArray(0)) {
        TerrainClient.client = HttpClient(MockEngine { respond(body, status) })
    }

    /** Serves each chunk the terrain [forChunk] gives for its x index. */
    private fun serveByChunk(forChunk: (cx: Int) -> ByteArray) {
        TerrainClient.client = HttpClient(MockEngine { request ->
            val e0 = request.url.parameters["bbox"].orEmpty().substringBefore(',').toInt()
            respond(forChunk((e0 + 2).floorDiv(500)), HttpStatusCode.OK)
        })
    }

    /** A chunk response 10 m higher above raster row [stepRow] than below: an east-west cliff. */
    private fun stepChunk(stepRow: Int): ByteArray {
        val tile = 128
        val tiles = (0 until 4).map { t -> FloatArray(tile * tile) { i -> if ((t / 2) * tile + i / tile < stepRow) 110f else 100f } }
        return littleEndianTiff(252, 252, tile, tiles)
    }

    private val flatChunk by lazy { stepChunk(0) }

    /** UTM chunk column of grid cell [x], [y]. */
    private fun TerrainGrid.chunkX(x: Int, y: Int) = Utm33.project(cellToLatLng(x, y)).first.toInt().floorDiv(500)

    /** 120 by 60 cells of flat ground in Rana, about 900 m north to south. */
    private fun ranaGrid(): TerrainGrid {
        val (px, py) = TileUtils.latLngToWorldPixel(LatLng(66.5, 14.3), 13)
        return TerrainGrid(60, 120, 13, px.toInt(), py.toInt(), 7.6, FloatArray(60 * 120) { 100f })
    }

    @Test
    fun utm33MatchesProj() {
        // Reference values from PROJ, including Bergen and Finnmark, far outside zone 33.
        val cases = listOf(
            LatLng(66.46254876701175, 14.30720329284668) to (469127.3917 to 7371639.7192),
            LatLng(60.39, 5.32) to (-32253.5971 to 6734074.9106),
            LatLng(71.17, 25.78) to (886515.6921 to 7930894.8947),
            LatLng(59.91, 10.75) to (262409.7321 to 6649017.7489),
        )
        for ((p, expected) in cases) {
            val (e, n) = Utm33.project(p)
            assertEquals(expected.first, e, 0.01, "easting of $p")
            assertEquals(expected.second, n, 0.01, "northing of $p")
        }
    }

    @Test
    fun readsTiledFloatTiffWithMissingTile() {
        // 3x2 image in 2x2 tiles: the right tile is padded past the edge, the left one is absent.
        val right = floatArrayOf(5f, 0f, 6f, 0f)
        val tiff = littleEndianTiff(width = 3, height = 2, tile = 2, tiles = listOf(null, right))
        val raster = FloatTiff.decode(tiff)
        assertEquals(3, raster.width)
        assertEquals(2, raster.height)
        assertTrue(raster[0, 0].isNaN())
        assertTrue(raster[1, 1].isNaN())
        assertEquals(5f, raster[2, 0])
        assertEquals(6f, raster[2, 1])
    }

    @Test
    fun steepMaskMarksOnlyTheStep() {
        val size = 252
        // A 6 m step between columns 100 and 101; no data in the last ten rows, and the service's
        // zero fill in the first ten.
        val values = FloatArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if (y >= size - 10) Float.NaN else if (y < 10) 0f else if (x > 100) 106f else 100f
        }
        val mask = CliffMap.steepMask(FloatRaster(size, size, values))
        fun steep(c: Int, r: Int) = (r * 250 + c).let { mask[it shr 3].toInt() and (1 shl (it and 7)) != 0 }
        // Interior column c is raster column c + 1, so the step sits between c = 99 and c = 100.
        assertTrue(steep(99, 50))
        assertTrue(steep(100, 50))
        assertFalse(steep(98, 50))
        assertFalse(steep(101, 50))
        assertFalse(steep(99, 245), "no-data rows must not read as cliff")
        fun noData(c: Int, r: Int) = (r * 250 + c).let { mask[mask.size / 2 + (it shr 3)].toInt() and (1 shl (it and 7)) != 0 }
        assertTrue(noData(99, 245))
        assertTrue(noData(50, 3), "zero fill is no data")
        assertFalse(steep(50, 8), "the edge of the zero fill is no cliff")
        assertFalse(noData(99, 50))
    }

    @Test
    fun closesOneCellGapInCliffLineOnly() {
        val g = TerrainGrid(6, 6, 13, 0, 0, 10.0, FloatArray(36))
        g.addFlag(g.index(1, 1), CellFlags.CLIFF)
        g.addFlag(g.index(3, 3), CellFlags.CLIFF)
        g.addFlag(g.index(5, 0), CellFlags.CLIFF)
        g.addFlag(g.index(3, 2), CellFlags.CLIFF)
        val checked = BooleanArray(36) { it != g.index(4, 1) }
        CliffMap.closeGaps(g, IntArray(36) { it }, checked)
        // Diagonal gap between two cliffs closes; a cell next to just one cliff doesn't.
        assertTrue(g.hasFlag(g.index(2, 2), CellFlags.CLIFF))
        assertFalse(g.hasFlag(g.index(2, 1), CellFlags.CLIFF))
        // A gap between (5,0) and (3,2), but unchecked: no data to close it with.
        assertFalse(g.hasFlag(g.index(4, 1), CellFlags.CLIFF))
    }

    @Test
    fun knightMoveOverOneCellCliffIsNotWalkable() {
        val g = TerrainGrid(10, 10, 13, 0, 0, 10.0, FloatArray(100) { 100f })
        // (2,2) -> (4,3) passes through (3,2) and (3,3).
        val route = intArrayOf(g.index(2, 2), g.index(4, 3))
        assertTrue(TerrainRouter.walkable(g, route, HikingCost.blockedCells(g)))
        g.addFlag(g.index(3, 2), CellFlags.CLIFF)
        assertFalse(TerrainRouter.walkable(g, route, HikingCost.blockedCells(g)))
    }

    @Test
    fun diagonalBetweenTwoCliffsAndBlockedStartAreNotWalkable() {
        val g = TerrainGrid(10, 10, 13, 0, 0, 10.0, FloatArray(100) { 100f })
        val diagonal = intArrayOf(g.index(2, 2), g.index(3, 3))
        g.addFlag(g.index(3, 2), CellFlags.CLIFF)
        assertTrue(TerrainRouter.walkable(g, diagonal, HikingCost.blockedCells(g)))
        g.addFlag(g.index(2, 3), CellFlags.CLIFF)
        assertFalse(TerrainRouter.walkable(g, diagonal, HikingCost.blockedCells(g)))
        g.addFlag(g.index(5, 5), CellFlags.CLIFF)
        assertFalse(TerrainRouter.walkable(g, intArrayOf(g.index(5, 5), g.index(6, 5)), HikingCost.blockedCells(g)))
    }

    @Test
    fun cliffOnPathIsOnlyFreeAlongThePath() {
        val g = TerrainGrid(10, 10, 13, 0, 0, 10.0, FloatArray(100) { 100f })
        for (x in 0 until 10) g.addFlag(g.index(x, 5), CellFlags.PATH)
        g.addFlag(g.index(5, 5), CellFlags.CLIFF)
        val blocked = HikingCost.blockedCells(g)
        assertTrue(TerrainRouter.walkable(g, intArrayOf(g.index(4, 5), g.index(5, 5), g.index(6, 5)), blocked))
        assertFalse(TerrainRouter.walkable(g, intArrayOf(g.index(5, 4), g.index(5, 5), g.index(6, 5)), blocked))
        assertEquals(listOf(g.cellToLatLng(5, 5)), TerrainRouter.cliffSpots(g, intArrayOf(g.index(5, 4), g.index(5, 5), g.index(6, 5))))
        // Leaving the path from the cliff cell is as much a cliff step as joining it there.
        val offPath = intArrayOf(g.index(4, 5), g.index(5, 5), g.index(5, 6))
        assertFalse(TerrainRouter.walkable(g, offPath, blocked))
        assertEquals(listOf(g.cellToLatLng(5, 5)), TerrainRouter.cliffSpots(g, offPath))
    }

    @Test
    fun cliffSlippedPastIsMarked() {
        val g = TerrainGrid(10, 10, 13, 0, 0, 10.0, FloatArray(100) { 100f })
        g.addFlag(g.index(3, 2), CellFlags.CLIFF)
        assertEquals(listOf(g.cellToLatLng(3, 2)), TerrainRouter.cliffSpots(g, intArrayOf(g.index(2, 2), g.index(4, 3))))
        // A diagonal beside one cliff cell is fine; between two it isn't.
        val diagonal = intArrayOf(g.index(2, 2), g.index(3, 3))
        assertTrue(TerrainRouter.cliffSpots(g, diagonal).isEmpty())
        g.addFlag(g.index(2, 3), CellFlags.CLIFF)
        assertEquals(1, TerrainRouter.cliffSpots(g, diagonal).size)
    }

    @Test
    fun rebuiltRouteKeepsSmoothedPointsOutOfNewCliffs() {
        val g = TerrainGrid(20, 20, 13, 1_100_000, 600_000, 10.0, FloatArray(400) { 100f })
        // East along row 10, then north up column 15: smoothing cuts the inside of the bend.
        val cells = ((5..15).map { g.index(it, 10) } + (9 downTo 4).map { g.index(15, it) }).toIntArray()
        fun pointCells() = TerrainRouter.rebuild(g, cells, HikingCost.blockedCells(g)).points.map { g.latLngToIndex(it.latLng) }
        val cut = pointCells().first { it !in cells }
        g.addFlag(cut, CellFlags.CLIFF)
        assertTrue(cut !in pointCells())
    }

    @Test
    fun smoothedLineDoesNotCutOverCliffBetweenPoints() {
        val g = TerrainGrid(20, 20, 13, 1_100_000, 600_000, 10.0, FloatArray(400) { 100f })
        // East along row 10, then a knight move and north up column 16: smoothing cuts the bend.
        val cells = ((5..14).map { g.index(it, 10) } + listOf(g.index(16, 9)) + (8 downTo 3).map { g.index(16, it) }).toIntArray()
        fun crossedCells(): Set<Int> {
            val points = TerrainRouter.rebuild(g, cells, HikingCost.blockedCells(g)).points.map { it.latLng }
            return points.zipWithNext().flatMap { (a, b) ->
                (0..40).map { k ->
                    val t = k / 40.0
                    g.latLngToIndex(LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
                }
            }.toSet()
        }
        val pointCells = TerrainRouter.rebuild(g, cells, HikingCost.blockedCells(g)).points.map { g.latLngToIndex(it.latLng) }.toSet()
        // A cell the line passes through between two points, so the per-point check can't see it.
        val between = crossedCells().first { it !in cells && it !in pointCells }
        g.addFlag(between, CellFlags.CLIFF)
        assertTrue(between !in crossedCells())
    }

    @Test
    fun cliffBandWithNoWayAroundIsClimbedOnceAndMarked() = runTest {
        // Every chunk has a 10 m step halfway down: an east-west cliff every 500 m of northing.
        serve(HttpStatusCode.OK, stepChunk(126))
        val g = ranaGrid()
        val from = g.cellToLatLng(30, 2)
        val to = g.cellToLatLng(30, 117)
        val result = CliffMap.refine(g, from, to, assertNotNull(TerrainRouter.route(g, from, to)))
        assertTrue(result.complete)
        // About 875 m north to south crosses one or two bands, each one cell thick at most twice.
        assertTrue(result.cliffs.size in 1..2, "cliffs=${result.cliffs}")
    }

    @Test
    fun reroutesAroundCliffIntoTheNextChunk() = runTest {
        val (px, py) = TileUtils.latLngToWorldPixel(LatLng(66.5, 14.3), 13)
        val g = TerrainGrid(200, 120, 13, px.toInt(), py.toInt(), 7.6, FloatArray(200 * 120) { 100f })
        val from = g.cellToLatLng(60, 2)
        val to = g.cellToLatLng(60, 117)
        val cliffColumn = g.chunkX(60, 60)
        serveByChunk { cx -> if (cx == cliffColumn) stepChunk(126) else flatChunk }
        val result = CliffMap.refine(g, from, to, assertNotNull(TerrainRouter.route(g, from, to)))
        assertTrue(result.cliffs.isEmpty())
        assertTrue(result.complete)
        assertTrue(result.route.cells.none { g.hasFlag(it, CellFlags.CLIFF) })
        assertTrue(result.route.cells.any { g.chunkX(it % g.width, it / g.width) != cliffColumn })
    }

    @Test
    fun cliffNearChunkNorthEdgeLandsThere() = runTest {
        // Step between raster rows 9 and 10: about 18 m below the chunk's north edge.
        serve(HttpStatusCode.OK, stepChunk(10))
        val g = ranaGrid()
        val from = g.cellToLatLng(30, 2)
        val to = g.cellToLatLng(30, 117)
        CliffMap.refine(g, from, to, assertNotNull(TerrainRouter.route(g, from, to)))
        val northings = (0 until g.width * g.height).filter { g.hasFlag(it, CellFlags.CLIFF) }
            .map { Utm33.project(g.cellToLatLng(it % g.width, it / g.width)).second.mod(500.0) }
        assertTrue(northings.isNotEmpty())
        assertTrue(northings.all { it in 470.0..495.0 }, "northings=$northings")
    }

    @Test
    fun chunkRequestCoversNegativeEastings() {
        // Bergen lies west of zone 33, at easting -32 km.
        val url = CliffMap.chunkUrl(-65, 13468)
        assertTrue("bbox=-32502,6733998,-31998,6734502&width=252&height=252" in url, url)
    }

    @Test
    fun missingTerrainDataIsIncomplete() = runTest {
        serve(HttpStatusCode.NotFound)
        val g = ranaGrid()
        val from = g.cellToLatLng(30, 2)
        val to = g.cellToLatLng(30, 117)
        val result = CliffMap.refine(g, from, to, assertNotNull(TerrainRouter.route(g, from, to)))
        assertTrue(result.cliffs.isEmpty())
        assertFalse(result.complete)
    }

    @Test
    fun cliffCellsBlockExceptOnPaths() {
        val g = TerrainGrid(3, 1, 13, 0, 0, 10.0, FloatArray(3) { 100f })
        g.addFlag(0, CellFlags.CLIFF)
        g.addFlag(1, CellFlags.CLIFF)
        g.addFlag(1, CellFlags.PATH)
        val blocked = HikingCost.blockedCells(g)
        assertTrue(blocked[0])
        assertFalse(blocked[1])
        assertFalse(blocked[2])
    }

    @Test
    fun routeGoesThroughGapInCliffBand() = runTest {
        val w = 60
        val g = TerrainGrid(w, 40, 13, 1_100_000, 600_000, 10.0, FloatArray(w * 40) { 100f })
        for (x in 0 until w) if (x !in 50..52) g.addFlag(g.index(x, 20), CellFlags.CLIFF)
        val route = assertNotNull(TerrainRouter.route(g, g.cellToLatLng(10, 5), g.cellToLatLng(10, 35)))
        assertTrue(route.cells.none { g.hasFlag(it, CellFlags.CLIFF) })
        assertTrue(route.cells.any { it % w in 50..52 })
    }

    private fun littleEndianTiff(width: Int, height: Int, tile: Int, tiles: List<FloatArray?>): ByteArray {
        val out = ArrayList<Byte>()
        fun u16(v: Int) { out += v.toByte(); out += (v shr 8).toByte() }
        fun u32(v: Int) { u16(v and 0xFFFF); u16(v ushr 16) }
        val tileBytes = tile * tile * 4
        val dataStart = 8
        val present = tiles.filterNotNull()
        val offsetsAt = dataStart + present.size * tileBytes
        val countsAt = offsetsAt + 4 * tiles.size
        val ifdAt = countsAt + 4 * tiles.size
        out += 'I'.code.toByte(); out += 'I'.code.toByte(); u16(42); u32(ifdAt)
        present.forEach { t -> t.forEach { u32(it.toRawBits()) } }
        var next = dataStart
        tiles.forEach { if (it == null) u32(0) else { u32(next); next += tileBytes } }
        tiles.forEach { u32(if (it == null) 0 else tileBytes) }
        val entries = listOf(
            Triple(256, 3, width), Triple(257, 3, height), Triple(258, 3, 32), Triple(259, 3, 1),
            Triple(322, 3, tile), Triple(323, 3, tile), Triple(339, 3, 3),
        )
        u16(entries.size + 2)
        entries.forEach { (code, type, v) -> u16(code); u16(type); u32(1); u32(v) }
        u16(324); u16(4); u32(tiles.size); u32(offsetsAt)
        u16(325); u16(4); u32(tiles.size); u32(countsAt)
        u32(0)
        return out.toByteArray()
    }
}
