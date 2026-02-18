package no.synth.where.util

expect object CrashReporter {
    fun setEnabled(enabled: Boolean)
    fun log(message: String)
}
