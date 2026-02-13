package no.synth.where.util

object NamingUtils {
    fun makeUnique(baseName: String, existingNames: Collection<String>): String {
        if (!existingNames.contains(baseName)) {
            return baseName
        }

        var counter = 2
        while (existingNames.contains("$baseName ($counter)")) {
            counter++
        }
        return "$baseName ($counter)"
    }
}

