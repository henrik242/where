package no.synth.where.data

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual object DemTileDecoder {
    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val image = ImageIO.read(ByteArrayInputStream(pngData)) ?: return null
        if (pixelX < 0 || pixelX >= image.width || pixelY < 0 || pixelY >= image.height) return null
        val argb = image.getRGB(pixelX, pixelY)
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff
        // Terrarium encoding: elevation = (r * 256 + g + b / 256) - 32768
        return (r * 256.0 + g + b / 256.0) - 32768.0
    }
}
