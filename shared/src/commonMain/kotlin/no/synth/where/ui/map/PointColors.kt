package no.synth.where.ui.map

/**
 * Palette offered when picking a saved-point color, as (hex, display name) pairs: warm earth tones
 * plus a few bright markers that pop on the map. Shared by the Android, iOS, and common
 * point-editing dialogs so the choices stay in sync.
 */
object PointColors {
    /** Default color for a new point (also the fallback when a point has none). */
    const val DEFAULT = "#FF5722"

    val palette = listOf(
        "#FF5722" to "Rust",
        "#8D6E63" to "Taupe",
        "#4CAF50" to "Green",
        "#FFC107" to "Amber",
        "#6B8E23" to "Olive",
        "#FF9800" to "Orange",
        "#4E6E58" to "Pine",
        "#7B3F3F" to "Maroon",
    )

    /**
     * The palette, plus [current] as a leading "Current" swatch when it isn't already a preset — so
     * a point saved with an old/removed color stays visible and re-selectable in the picker.
     */
    fun withSelected(current: String?): List<Pair<String, String>> {
        if (current.isNullOrBlank() || palette.any { it.first.equals(current, ignoreCase = true) }) {
            return palette
        }
        return listOf(current to "Current") + palette
    }
}
