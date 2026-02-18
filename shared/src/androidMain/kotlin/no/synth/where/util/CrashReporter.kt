package no.synth.where.util

import com.google.firebase.crashlytics.FirebaseCrashlytics

actual object CrashReporter {
    actual fun setEnabled(enabled: Boolean) {
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }

    actual fun log(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}
