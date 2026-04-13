package no.synth.where.data

data class LayerStats(val sizeBytes: Long, val tileCount: Int) {
    companion object {
        val EMPTY = LayerStats(0L, 0)
    }
}
