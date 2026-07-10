package no.synth.where.ui.map

/**
 * Colors for track lines drawn on the map: a cohesive earth-tone set chosen to stay distinct from
 * each other over Kartverket's topo tints; [forIndex] wraps so any number of simultaneously-viewed
 * tracks gets a stable color by its position in the viewing set.
 */
object TrackColors {
    val palette = listOf(
        "#A34234", // brick
        "#C4622D", // terracotta
        "#C99A2E", // ochre
        "#6B8E23", // olive
        "#4E6E58", // pine
        "#6D4C41", // brown
        "#8D6E63", // taupe
        "#7B5E3B", // umber
    )

    /** Recording (in-progress) track color, kept separate from the viewing palette. */
    const val RECORDING = "#FF0000"

    /** Grey for the trimmed-off ends shown while cropping (matches the navigation completed-line). */
    const val TRIMMED = "#9E9E9E"

    fun forIndex(index: Int): String {
        val size = palette.size
        return palette[((index % size) + size) % size]
    }
}
