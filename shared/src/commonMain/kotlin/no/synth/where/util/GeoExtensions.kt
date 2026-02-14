package no.synth.where.util

import kotlin.math.pow
import kotlin.math.roundToLong

private fun Double.roundToDecimals(decimals: Int): String {
    if (decimals == 0) return this.roundToLong().toString()
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToLong() / factor
    val parts = rounded.toString().split(".")
    val intPart = parts[0]
    val fracPart = if (parts.size > 1) parts[1] else ""
    return "$intPart.${fracPart.padEnd(decimals, '0').take(decimals)}"
}

fun Double.formatDistance(): String = when {
    this < 1000 -> "${this.roundToDecimals(0)} m"
    this < 10000 -> "${(this / 1000).roundToDecimals(2)} km"
    else -> "${(this / 1000).roundToDecimals(1)} km"
}

fun Double.formatKm(decimals: Int = 2): String = (this / 1000.0).roundToDecimals(decimals)
