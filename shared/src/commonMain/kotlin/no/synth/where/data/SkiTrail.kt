package no.synth.where.data

data class SkiTrail(
    val name: String,
    val coordinates: List<List<Double>>,
    val lit: Boolean = false,
    val difficulty: String? = null
)
