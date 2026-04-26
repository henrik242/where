package no.synth.where.util

/**
 * Coarsest non-zero granularity of a positive remaining-time value, picked
 * so callers can map directly to a single localized resource format.
 */
sealed class RemainingTime {
    data object Zero : RemainingTime()
    data class HoursOnly(val hours: Int) : RemainingTime()
    data class HoursAndMinutes(val hours: Int, val minutes: Int) : RemainingTime()
    data class MinutesOnly(val minutes: Int) : RemainingTime()
    data class SecondsOnly(val seconds: Int) : RemainingTime()
}

fun remainingTimeOf(millis: Long): RemainingTime {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val h = (totalSeconds / 3600L).toInt()
    val m = ((totalSeconds % 3600L) / 60L).toInt()
    val s = (totalSeconds % 60L).toInt()
    return when {
        h <= 0 && m <= 0 && s <= 0 -> RemainingTime.Zero
        h > 0 && m == 0 -> RemainingTime.HoursOnly(h)
        h > 0 -> RemainingTime.HoursAndMinutes(h, m)
        m > 0 -> RemainingTime.MinutesOnly(m)
        else -> RemainingTime.SecondsOnly(s)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> {
        val mb = bytes / (1024.0 * 1024.0)
        val whole = mb.toLong()
        val frac = ((mb - whole) * 10 + 0.5).toInt()
        if (frac >= 10) "${whole + 1}.0 MB" else "$whole.$frac MB"
    }
}
