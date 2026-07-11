package no.synth.where.util

interface CrashReporterBridge {
    fun setEnabled(enabled: Boolean)
    fun log(message: String)
    fun recordException(message: String)
}

actual object CrashReporter {
    var bridge: CrashReporterBridge? = null

    actual fun setEnabled(enabled: Boolean) {
        bridge?.setEnabled(enabled) ?: Logger.d("CrashReporter: setEnabled(%s)", enabled.toString())
    }

    actual fun log(message: String) {
        bridge?.log(message) ?: Logger.d("CrashReporter: %s", message)
    }

    actual fun recordException(message: String) {
        bridge?.recordException(message) ?: Logger.d("CrashReporter: recordException(%s)", message)
    }
}
