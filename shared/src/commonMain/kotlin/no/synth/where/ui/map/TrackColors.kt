package no.synth.where.ui.map

/**
 * Colors for track lines drawn on the map. The [palette] hues are chosen to stay distinct from
 * each other and readable over Kartverket's yellow/green topo tints; [forIndex] wraps so any
 * number of simultaneously-viewed tracks gets a stable color by its position in the viewing set.
 */
object TrackColors {
    val palette = listOf(
        "#1E88E5", // blue
        "#43A047", // green
        "#8E24AA", // purple
        "#FB8C00", // orange
        "#00897B", // teal
        "#C2185B", // pink
        "#3949AB", // indigo
        "#6D4C41", // brown
    )

    /** Recording (in-progress) track color, kept separate from the viewing palette. */
    const val RECORDING = "#FF0000"

    fun forIndex(index: Int): String {
        val size = palette.size
        return palette[((index % size) + size) % size]
    }
}
