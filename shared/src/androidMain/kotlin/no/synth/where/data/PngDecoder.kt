package no.synth.where.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace

actual object PngDecoder {
    actual fun decode(png: ByteArray): RgbImage? {
        // Unpremultiplied sRGB, so channel bytes come back as stored.
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, options) ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            for (i in pixels.indices) pixels[i] = pixels[i] and 0xFFFFFF
            return RgbImage(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }
}
