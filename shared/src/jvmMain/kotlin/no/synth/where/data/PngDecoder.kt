package no.synth.where.data

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual object PngDecoder {
    actual fun decode(png: ByteArray): RgbImage? {
        val image = ImageIO.read(ByteArrayInputStream(png)) ?: return null
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        for (i in pixels.indices) pixels[i] = pixels[i] and 0xFFFFFF
        return RgbImage(image.width, image.height, pixels)
    }
}
