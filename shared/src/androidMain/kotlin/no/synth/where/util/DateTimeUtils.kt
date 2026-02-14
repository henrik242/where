package no.synth.where.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatDateTime(epochMillis: Long, pattern: String): String {
    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return dateFormat.format(Date(epochMillis))
}
