package no.synth.where.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object HmacUtils {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    actual fun generateSignature(data: String, secretKey: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKeySpec)
        val rawHmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return kotlin.io.encoding.Base64.Default.encode(rawHmac)
    }
}
