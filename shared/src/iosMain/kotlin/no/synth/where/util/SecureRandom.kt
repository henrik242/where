package no.synth.where.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

@OptIn(ExperimentalForeignApi::class)
actual fun secureRandomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, byteCount.toULong(), pinned.addressOf(0))
    }
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
