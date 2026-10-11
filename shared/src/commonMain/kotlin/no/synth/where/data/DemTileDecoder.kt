package no.synth.where.data

expect object DemTileDecoder {
    fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double?

    /** All pixels of a Terrarium tile as row-major elevations, or null if undecodable. */
    fun decodeTile(pngData: ByteArray): DemTile?
}

class DemTile(val width: Int, val height: Int, val elevations: FloatArray)

// Terrarium encoding: elevation = (r * 256 + g + b / 256) - 32768
internal fun terrariumElevation(r: Int, g: Int, b: Int): Double = (r * 256.0 + g + b / 256.0) - 32768.0
