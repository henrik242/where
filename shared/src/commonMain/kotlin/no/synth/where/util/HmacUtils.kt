package no.synth.where.util

expect object HmacUtils {
    fun generateSignature(data: String, secretKey: String): String
}
