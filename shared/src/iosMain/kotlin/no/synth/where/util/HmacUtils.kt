package no.synth.where.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual object HmacUtils {
    actual fun generateSignature(data: String, secretKey: String): String {
        val dataBytes = data.encodeToByteArray()
        val keyBytes = secretKey.encodeToByteArray()
        val digestLength = CC_SHA256_DIGEST_LENGTH
        val hmacOut = ByteArray(digestLength)

        keyBytes.usePinned { keyPinned ->
            dataBytes.usePinned { dataPinned ->
                hmacOut.usePinned { outPinned ->
                    CCHmac(
                        kCCHmacAlgSHA256,
                        keyPinned.addressOf(0),
                        keyBytes.size.toULong(),
                        dataPinned.addressOf(0),
                        dataBytes.size.toULong(),
                        outPinned.addressOf(0)
                    )
                }
            }
        }

        return kotlin.io.encoding.Base64.Default.encode(hmacOut)
    }
}
