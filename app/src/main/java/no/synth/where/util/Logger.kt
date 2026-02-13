package no.synth.where.util

import timber.log.Timber

object Logger {
    fun d(message: String, vararg args: Any?) {
        Timber.d(message, *args)
    }

    fun w(message: String, vararg args: Any?) {
        Timber.w(message, *args)
    }

    fun e(message: String, vararg args: Any?) {
        Timber.e(message, *args)
    }

    fun e(throwable: Throwable, message: String, vararg args: Any?) {
        Timber.e(throwable, message, *args)
    }
}
