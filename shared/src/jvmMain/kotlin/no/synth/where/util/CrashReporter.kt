package no.synth.where.util

/** Desktop has no Firebase Crashlytics; breadcrumbs and non-fatals go to the logger. */
actual object CrashReporter {
    actual fun setEnabled(enabled: Boolean) {}

    actual fun log(message: String) {
        Logger.d(message)
    }

    actual fun recordException(message: String) {
        Logger.e(message)
    }
}
