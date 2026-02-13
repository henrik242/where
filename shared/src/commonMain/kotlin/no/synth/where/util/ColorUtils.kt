package no.synth.where.util

import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String): Color {
    val stripped = hex.removePrefix("#")
    val argb = when (stripped.length) {
        6 -> (0xFF000000 or stripped.toLong(16)).toInt()
        8 -> stripped.toLong(16).toInt()
        else -> throw IllegalArgumentException("Invalid hex color: $hex")
    }
    return Color(argb)
}
