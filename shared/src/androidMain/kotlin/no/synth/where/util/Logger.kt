package no.synth.where.util

import timber.log.Timber

actual object Logger {
    actual fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }

    actual fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }

    actual fun e(message: String, vararg args: Any?) {
        Timber.e(message, *args)
    }

    actual fun e(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.e(throwable, message, *args)
    }
}
