package no.synth.where.util

import java.security.SecureRandom

private val rng = SecureRandom()

actual fun secureRandomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    rng.nextBytes(bytes)
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
