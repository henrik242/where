package no.synth.where.util

actual object Logger {
    actual fun d(message: String, vararg args: Any?) {
        println("D: ${formatMessage(message, args)}")
    }

    actual fun w(message: String, vararg args: Any?) {
        println("W: ${formatMessage(message, args)}")
    }

    actual fun e(message: String, vararg args: Any?) {
        println("E: ${formatMessage(message, args)}")
    }

    actual fun e(throwable: Throwable, message: String, vararg args: Any?) {
        println("E: ${formatMessage(message, args)}\n${throwable.stackTraceToString()}")
    }

    private fun formatMessage(message: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return message
        var result = message
        for (arg in args) {
            val index = result.indexOf("%s")
            if (index != -1) {
                result = result.replaceRange(index, index + 2, arg.toString())
            }
        }
        return result
    }
}
