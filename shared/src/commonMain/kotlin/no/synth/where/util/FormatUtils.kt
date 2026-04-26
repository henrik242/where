package no.synth.where.util

/**
 * Decomposes a remaining-time value into a (hours, minutes, seconds) triple.
 * Negative or zero millis yields all-zeros. Callers pick the appropriate
 * resource format based on which fields are non-zero.
 */
data class RemainingTime(val hours: Int, val minutes: Int, val seconds: Int) {
    val isHoursOnly: Boolean get() = hours > 0 && minutes == 0
    val isHoursAndMinutes: Boolean get() = hours > 0 && minutes > 0
    val isMinutesOnly: Boolean get() = hours == 0 && minutes > 0
    val isSecondsOnly: Boolean get() = hours == 0 && minutes == 0
}

fun remainingTimeOf(millis: Long): RemainingTime {
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val h = (totalSeconds / 3600L).toInt()
    val m = ((totalSeconds % 3600L) / 60L).toInt()
    val s = (totalSeconds % 60L).toInt()
    return RemainingTime(h, m, s)
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
