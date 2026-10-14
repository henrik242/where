package no.synth.where.data

/** A decoded image as row-major 0xRRGGBB pixels, alpha dropped. */
class RgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    fun rgbAt(x: Int, y: Int): Int? =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else null
}

expect object PngDecoder {
    /**
     * Channel values exactly as stored, without colour conversion; null if undecodable.
     * Only opaque pixels are exact everywhere: iOS blends translucent ones onto black.
     */
    fun decode(png: ByteArray): RgbImage?
}
