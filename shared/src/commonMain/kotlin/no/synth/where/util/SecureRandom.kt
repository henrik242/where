package no.synth.where.util

/** Cryptographically secure random hex string of [byteCount] bytes (2 hex chars each). */
expect fun secureRandomHex(byteCount: Int): String
