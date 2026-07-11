package no.synth.where.util

expect object CrashReporter {
    fun setEnabled(enabled: Boolean)

    /** Breadcrumb attached to the next crash/non-fatal report; not an event on its own. */
    fun log(message: String)

    /** Records a non-fatal event that surfaces in the crash dashboard on its own (no crash needed). */
    fun recordException(message: String)
}
