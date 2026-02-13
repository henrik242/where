package no.synth.where.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun `parseHexColor parses 6-digit hex with hash`() {
        val color = parseHexColor("#FF5722")
        assertEquals(Color(0xFFFF5722), color)
    }

    @Test
    fun `parseHexColor parses 6-digit hex without hash`() {
        val color = parseHexColor("2196F3")
        assertEquals(Color(0xFF2196F3), color)
    }

    @Test
    fun `parseHexColor parses 8-digit hex with alpha`() {
        val color = parseHexColor("#80FF5722")
        assertEquals(Color(0x80FF5722.toInt()), color)
    }

    @Test
    fun `parseHexColor parses black`() {
        val color = parseHexColor("#000000")
        assertEquals(Color(0xFF000000), color)
    }

    @Test
    fun `parseHexColor parses white`() {
        val color = parseHexColor("#FFFFFF")
        assertEquals(Color(0xFFFFFFFF), color)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseHexColor rejects invalid length`() {
        parseHexColor("#FFF")
    }
}
