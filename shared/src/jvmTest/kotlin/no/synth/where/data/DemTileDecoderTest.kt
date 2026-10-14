package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DemTileDecoderTest {
    @Test
    fun decodesTerrariumElevation() {
        // (r * 256 + g + b / 256) - 32768
        val png = pngOf(0xFF800000.toInt(), 0xFF812C80.toInt(), 0xFF7FFF00.toInt())
        assertEquals(0.0, DemTileDecoder.decodeElevation(png, 0, 0))
        assertEquals(300.5, DemTileDecoder.decodeElevation(png, 1, 0))
        assertEquals(-1.0, DemTileDecoder.decodeElevation(png, 2, 0))
        val tile = assertNotNull(DemTileDecoder.decodeTile(png))
        assertEquals(listOf(0f, 300.5f, -1f), tile.elevations.toList())
    }
}
