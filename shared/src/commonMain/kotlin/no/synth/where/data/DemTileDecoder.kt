package no.synth.where.data

object DemTileDecoder {
    fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double? =
        PngDecoder.decode(pngData)?.rgbAt(pixelX, pixelY)?.let(::elevationOf)

    /** All pixels of a Terrarium tile as row-major elevations, or null if undecodable. */
    fun decodeTile(pngData: ByteArray): DemTile? {
        val image = PngDecoder.decode(pngData) ?: return null
        val out = FloatArray(image.pixels.size) { i -> elevationOf(image.pixels[i]).toFloat() }
        return DemTile(image.width, image.height, out)
    }
}

class DemTile(val width: Int, val height: Int, val elevations: FloatArray)

// Terrarium encoding: elevation = (r * 256 + g + b / 256) - 32768
private fun elevationOf(rgb: Int): Double =
    ((rgb shr 16) and 0xFF) * 256.0 + ((rgb shr 8) and 0xFF) + (rgb and 0xFF) / 256.0 - 32768.0
