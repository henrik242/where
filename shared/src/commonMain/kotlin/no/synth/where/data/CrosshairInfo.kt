package no.synth.where.data

data class CrosshairInfo(
    val elevation: Double? = null,
    val slopeDegrees: Double? = null,
    val isLoading: Boolean = false,
    /** Null while the coverage overlay is hidden; loads on its own so terrain isn't held up. */
    val coverage: CoverageLookup? = null,
)
