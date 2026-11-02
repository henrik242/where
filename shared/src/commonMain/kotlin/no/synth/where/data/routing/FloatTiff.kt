package no.synth.where.data.routing

/** Single-band, uncompressed 32-bit float raster. */
internal class FloatRaster(val width: Int, val height: Int, val values: FloatArray) {
    operator fun get(x: Int, y: Int) = values[y * width + x]
}

/**
 * Minimal TIFF reader for the uncompressed float32 GeoTIFFs Kartverket's WCS returns, striped or
 * tiled. Strips or tiles left out of the file (no data) read as NaN.
 */
internal object FloatTiff {
    private const val IMAGE_WIDTH = 256
    private const val IMAGE_LENGTH = 257
    private const val BITS_PER_SAMPLE = 258
    private const val COMPRESSION = 259
    private const val STRIP_OFFSETS = 273
    private const val SAMPLES_PER_PIXEL = 277
    private const val ROWS_PER_STRIP = 278
    private const val STRIP_BYTE_COUNTS = 279
    private const val TILE_WIDTH = 322
    private const val TILE_LENGTH = 323
    private const val TILE_OFFSETS = 324
    private const val TILE_BYTE_COUNTS = 325
    private const val SAMPLE_FORMAT = 339
    private const val SAMPLE_FORMAT_FLOAT = 3

    fun decode(data: ByteArray): FloatRaster {
        val r = Reader(data)
        val tags = r.readIfd()
        fun tag(code: Int) = tags[code] ?: throw IllegalArgumentException("TIFF tag $code missing")
        val width = tag(IMAGE_WIDTH)[0].toInt()
        val height = tag(IMAGE_LENGTH)[0].toInt()
        require(tag(BITS_PER_SAMPLE)[0] == 32L) { "not 32-bit" }
        require((tags[COMPRESSION]?.get(0) ?: 1L) == 1L) { "compressed TIFF" }
        require((tags[SAMPLES_PER_PIXEL]?.get(0) ?: 1L) == 1L) { "multi-band TIFF" }
        require(tags[SAMPLE_FORMAT]?.get(0) == SAMPLE_FORMAT_FLOAT.toLong()) { "not float" }

        val out = FloatArray(width * height) { Float.NaN }
        val tiled = tags.containsKey(TILE_OFFSETS)
        val blockW = if (tiled) tag(TILE_WIDTH)[0].toInt() else width
        val blockH = if (tiled) tag(TILE_LENGTH)[0].toInt() else (tags[ROWS_PER_STRIP]?.get(0)?.toInt() ?: height).coerceAtMost(height)
        val offsets = tag(if (tiled) TILE_OFFSETS else STRIP_OFFSETS)
        val counts = tag(if (tiled) TILE_BYTE_COUNTS else STRIP_BYTE_COUNTS)
        val across = (width + blockW - 1) / blockW
        for (b in offsets.indices) {
            if (counts[b] == 0L) continue
            val x0 = (b % across) * blockW
            val y0 = (b / across) * blockH
            var pos = offsets[b].toInt()
            for (y in y0 until y0 + blockH) {
                for (x in x0 until x0 + blockW) {
                    // Tiles are padded past the image edge; skip the padding.
                    if (x < width && y < height) out[y * width + x] = Float.fromBits(r.int32(pos))
                    pos += 4
                }
            }
        }
        return FloatRaster(width, height, out)
    }

    private class Reader(private val d: ByteArray) {
        private val little = when {
            d.size >= 8 && d[0] == 'I'.code.toByte() && d[1] == 'I'.code.toByte() -> true
            d.size >= 8 && d[0] == 'M'.code.toByte() && d[1] == 'M'.code.toByte() -> false
            else -> throw IllegalArgumentException("not a TIFF")
        }

        fun u8(p: Int) = d[p].toInt() and 0xFF
        fun u16(p: Int) = if (little) u8(p) or (u8(p + 1) shl 8) else (u8(p) shl 8) or u8(p + 1)
        fun int32(p: Int) = if (little) {
            u8(p) or (u8(p + 1) shl 8) or (u8(p + 2) shl 16) or (u8(p + 3) shl 24)
        } else {
            (u8(p) shl 24) or (u8(p + 1) shl 16) or (u8(p + 2) shl 8) or u8(p + 3)
        }
        fun u32(p: Int) = int32(p).toLong() and 0xFFFFFFFFL

        /** Tag code -> integer values of the first IFD; non-integer tags are skipped. */
        fun readIfd(): Map<Int, LongArray> {
            require(u16(2) == 42) { "not a classic TIFF" }
            val ifd = u32(4).toInt()
            val n = u16(ifd)
            val tags = HashMap<Int, LongArray>()
            for (k in 0 until n) {
                val e = ifd + 2 + k * 12
                val code = u16(e)
                val type = u16(e + 2)
                val count = u32(e + 4).toInt()
                val size = when (type) {
                    3 -> 2 // SHORT
                    4 -> 4 // LONG
                    else -> continue
                }
                val base = if (size * count <= 4) e + 8 else u32(e + 8).toInt()
                tags[code] = LongArray(count) { i -> if (size == 2) u16(base + 2 * i).toLong() else u32(base + 4 * i) }
            }
            return tags
        }
    }
}
