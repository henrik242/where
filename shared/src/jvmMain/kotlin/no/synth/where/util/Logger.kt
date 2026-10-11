package no.synth.where.util

/** Desktop logger: Timber-style format strings printed to stdout/stderr. */
actual object Logger {
    private fun format(message: String, args: Array<out Any?>): String =
        if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)

    actual fun d(message: String, vararg args: Any?) = println("D ${format(message, args)}")
    actual fun w(message: String, vararg args: Any?) = println("W ${format(message, args)}")

    actual fun w(throwable: Throwable, message: String, vararg args: Any?) {
        System.err.println("W ${format(message, args)}")
        throwable.printStackTrace()
    }

    actual fun e(message: String, vararg args: Any?) = System.err.println("E ${format(message, args)}")

    actual fun e(throwable: Throwable, message: String, vararg args: Any?) {
        System.err.println("E ${format(message, args)}")
        throwable.printStackTrace()
    }
}
