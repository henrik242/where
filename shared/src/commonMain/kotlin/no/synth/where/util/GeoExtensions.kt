package no.synth.where.util


fun Double.formatDistance(): String = when {
    this < 1000 -> "%.0f m".format(this)
    this < 10000 -> "%.2f km".format(this / 1000)
    else -> "%.1f km".format(this / 1000)
}

