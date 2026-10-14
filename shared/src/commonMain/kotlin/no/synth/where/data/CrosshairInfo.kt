package no.synth.where.data

data class CrosshairInfo(
    val elevation: Double? = null,
    val slopeDegrees: Double? = null,
    val isLoading: Boolean = false,
    /** True while the coverage overlay is shown; coverage loads on its own so terrain isn't held up. */
    val showCoverage: Boolean = false,
    val isCoverageLoading: Boolean = false,
    val coverage: CoverageAtPoint? = null,
    /** The coverage tile couldn't be fetched, as opposed to there being no data here. */
    val coverageFailed: Boolean = false,
)
