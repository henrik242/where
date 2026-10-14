package no.synth.where.data

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A PNG with the given ARGB pixels in one row. */
internal fun pngOf(vararg argb: Int): ByteArray {
    val image = BufferedImage(argb.size, 1, BufferedImage.TYPE_INT_ARGB)
    argb.forEachIndexed { x, pixel -> image.setRGB(x, 0, pixel) }
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}

/** A tile-sized PNG where every pixel is [argb]. */
internal fun uniformTile(argb: Int): ByteArray {
    val image = BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until 256) for (x in 0 until 256) image.setRGB(x, y, argb)
    return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}

class PngDecoderTest {

    @Test
    fun returnsChannelsWithoutAlpha() {
        val image = PngDecoder.decode(pngOf(0xFF000000.toInt(), 0x8075153E.toInt()))!!
        assertEquals(0x000000, image.rgbAt(0, 0))
        assertEquals(0x75153E, image.rgbAt(1, 0))
    }

    @Test
    fun outOfBoundsIsNull() {
        val image = PngDecoder.decode(pngOf(0xFF123456.toInt()))!!
        assertNull(image.rgbAt(1, 0))
        assertNull(image.rgbAt(0, -1))
    }

    @Test
    fun notAPngIsNull() {
        assertNull(PngDecoder.decode(byteArrayOf(1, 2, 3)))
    }
}
