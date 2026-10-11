package no.synth.where.data

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

actual object DemTileDecoder {
    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val image = ImageIO.read(ByteArrayInputStream(pngData)) ?: return null
        if (pixelX < 0 || pixelX >= image.width || pixelY < 0 || pixelY >= image.height) return null
        val argb = image.getRGB(pixelX, pixelY)
        return terrariumElevation((argb shr 16) and 0xff, (argb shr 8) and 0xff, argb and 0xff)
    }

    actual fun decodeTile(pngData: ByteArray): DemTile? {
        val image = ImageIO.read(ByteArrayInputStream(pngData)) ?: return null
        val w = image.width
        val h = image.height
        val argb = image.getRGB(0, 0, w, h, null, 0, w)
        val out = FloatArray(w * h) { i ->
            val p = argb[i]
            terrariumElevation((p shr 16) and 0xff, (p shr 8) and 0xff, p and 0xff).toFloat()
        }
        return DemTile(w, h, out)
    }
}
