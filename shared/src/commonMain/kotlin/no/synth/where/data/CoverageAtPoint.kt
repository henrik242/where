package no.synth.where.data

/** Coverage under the crosshair. */
sealed interface CoverageLookup {
    data object Loading : CoverageLookup
    /** The tile couldn't be fetched, as opposed to there being no data here. */
    data object Failed : CoverageLookup
    /** [coverage] is null outside Norway or at sea. */
    data class Found(val coverage: CoverageAtPoint?) : CoverageLookup
}

/** Call coverage per operator on erdetdekning.no's scale: 0 none, 1 weak, 2 good, 3 very good. */
data class CoverageAtPoint(val telenor: Int, val telia: Int, val ice: Int) {
    val operators: List<Pair<String, Int>> get() = listOf("Telenor" to telenor, "Telia" to telia, "Ice" to ice)

    /** Emergency calls use any available network, so the best operator decides. */
    val bestLevel: Int get() = maxOf(telenor, telia, ice)

    companion object {
        private const val OUTSIDE_NORWAY = 0x80

        /**
         * Decodes a pixel of the "levels" tiles: red is Telenor, green Telia, blue Ice, with the call
         * level in the low two bits. All three 0x80 means outside Norway, so no data.
         */
        fun fromRgb(rgb: Int): CoverageAtPoint? {
            if (rgb == OUTSIDE_NORWAY * 0x010101) return null
            return CoverageAtPoint(
                telenor = (rgb shr 16) and 0x03,
                telia = (rgb shr 8) and 0x03,
                ice = rgb and 0x03,
            )
        }
    }
}
