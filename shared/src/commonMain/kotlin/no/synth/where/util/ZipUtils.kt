package no.synth.where.util

import no.synth.kmpzip.io.ByteArrayInputStream
import no.synth.kmpzip.zip.ZipInputStream

fun extractFirstFileFromZip(zipData: ByteArray, extension: String): ByteArray? {
    ByteArrayInputStream(zipData).use { bais ->
        ZipInputStream(bais).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(extension, ignoreCase = true)) {
                    return zipInput.readBytes()
                }
                entry = zipInput.nextEntry
            }
        }
    }
    return null
}
