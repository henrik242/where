package no.synth.where.util

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacUtils {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    /**
     * Generate HMAC-SHA256 signature for the given data using the secret key
     */
    fun generateSignature(data: String, secretKey: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
        mac.init(secretKeySpec)
        val rawHmac = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(rawHmac, Base64.NO_WRAP)
    }
}

