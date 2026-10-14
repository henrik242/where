package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoverageAtPointTest {

    @Test
    fun decodesTheCallLevelOfEachChannel() {
        // Upper bits are data level and networks, which are ignored.
        val telenor = 0b0111_0101 // call 1
        val telia = 0b0001_0110 // call 2
        val ice = 0b0011_1111 // call 3
        assertEquals(CoverageAtPoint(1, 2, 3), CoverageAtPoint.fromRgb((telenor shl 16) or (telia shl 8) or ice))
    }

    @Test
    fun bestLevelIsTheBestOperator() {
        assertEquals(0, CoverageAtPoint(0, 0, 0).bestLevel)
        assertEquals(2, CoverageAtPoint(1, 0, 2).bestLevel)
    }

    @Test
    fun dataOnlyCoverageHasNoCallLevel() {
        assertEquals(CoverageAtPoint(0, 0, 0), CoverageAtPoint.fromRgb(0x0C0C0C))
    }

    @Test
    fun outsideNorwayNeedsAllThreeChannels() {
        assertNull(CoverageAtPoint.fromRgb(0x808080))
        assertEquals(CoverageAtPoint(0, 0, 0), CoverageAtPoint.fromRgb(0x800000))
    }
}
