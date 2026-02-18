package no.synth.where.util

interface CrashReporterBridge {
    fun setEnabled(enabled: Boolean)
    fun log(message: String)
}

actual object CrashReporter {
    var bridge: CrashReporterBridge? = null

    actual fun setEnabled(enabled: Boolean) {
        bridge?.setEnabled(enabled) ?: Logger.d("CrashReporter: setEnabled(%s)", enabled.toString())
    }

    actual fun log(message: String) {
        bridge?.log(message) ?: Logger.d("CrashReporter: %s", message)
    }
}
