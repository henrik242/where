package no.synth.where.util

import kotlin.experimental.ExperimentalNativeApi

interface CrashReporterBridge {
    fun setEnabled(enabled: Boolean)
    fun log(message: String)
    fun recordException(message: String)

    /** Records an uncaught Kotlin exception; Kotlin/Native's abort otherwise drops its type and stack. */
    fun recordFatal(name: String, reason: String, stackTrace: List<String>)
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

    @OptIn(ExperimentalNativeApi::class)
    fun installUnhandledExceptionHook() {
        setUnhandledExceptionHook { t ->
            bridge?.recordFatal(t::class.qualifiedName ?: "Throwable", t.message ?: "", t.getStackTrace().toList())
        }
    }
}
