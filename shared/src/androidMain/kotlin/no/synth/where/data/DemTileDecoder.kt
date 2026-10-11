package no.synth.where.data

import android.graphics.BitmapFactory
import android.graphics.Color

actual object DemTileDecoder {
    actual fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? {
        val bitmap = BitmapFactory.decodeByteArray(pngData, 0, pngData.size) ?: return null
        try {
            if (pixelX < 0 || pixelX >= bitmap.width || pixelY < 0 || pixelY >= bitmap.height) return null
            val pixel = bitmap.getPixel(pixelX, pixelY)
            return terrariumElevation(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        } finally {
            bitmap.recycle()
        }
    }

    actual fun decodeTile(pngData: ByteArray): DemTile? {
        val bitmap = BitmapFactory.decodeByteArray(pngData, 0, pngData.size) ?: return null
        try {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = FloatArray(w * h) { i ->
                val p = pixels[i]
                terrariumElevation(Color.red(p), Color.green(p), Color.blue(p)).toFloat()
            }
            return DemTile(w, h, out)
        } finally {
            bitmap.recycle()
        }
    }
}
