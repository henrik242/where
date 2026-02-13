package no.synth.where.util

expect object Logger {
    fun d(message: String, vararg args: Any?)
    fun w(message: String, vararg args: Any?)
    fun e(message: String, vararg args: Any?)
    fun e(throwable: Throwable, message: String, vararg args: Any?)
}
