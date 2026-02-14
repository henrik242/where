package no.synth.where.util

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
