package no.synth.where.util

actual object CrashReporter {
    actual fun setEnabled(enabled: Boolean) {
        Logger.d("CrashReporter: setEnabled(%s)", enabled.toString())
    }

    actual fun log(message: String) {
        Logger.d("CrashReporter: %s", message)
    }
}
