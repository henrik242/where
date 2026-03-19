package no.synth.where.data

import android.graphics.BitmapFactory
import android.graphics.Color

actual object DemTileDecoder {
    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val bitmap = BitmapFactory.decodeByteArray(pngData, 0, pngData.size) ?: return null
        try {
            if (pixelX < 0 || pixelX >= bitmap.width || pixelY < 0 || pixelY >= bitmap.height) return null
            val pixel = bitmap.getPixel(pixelX, pixelY)
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Terrarium encoding: elevation = (r * 256 + g + b / 256) - 32768
            return (r * 256.0 + g + b / 256.0) - 32768.0
        } finally {
            bitmap.recycle()
        }
    }
}
