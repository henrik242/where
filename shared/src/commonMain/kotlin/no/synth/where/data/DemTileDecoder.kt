package no.synth.where.data

expect object DemTileDecoder {
    fun decodeElevation(pngData: ByteArray, pixelX: Int, pixelY: Int): Double?
}
