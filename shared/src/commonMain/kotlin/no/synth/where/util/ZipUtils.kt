package no.synth.where.util

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.openZip
import kotlin.random.Random

internal expect val platformFileSystem: FileSystem

fun extractFirstFileFromZip(zipData: ByteArray, extension: String): ByteArray? {
    if (zipData.isEmpty()) return null
    val tempFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY /
        "where-zip-${currentTimeMillis()}-${Random.nextInt(0, Int.MAX_VALUE)}.zip"
    return try {
        platformFileSystem.write(tempFile) { write(zipData) }
        val zipFs = platformFileSystem.openZip(tempFile)
        val match = zipFs.listRecursively("/".toPath())
            .firstOrNull { it.name.endsWith(extension, ignoreCase = true) }
        if (match != null) zipFs.read(match) { readByteArray() } else null
    } catch (_: Exception) {
        null
    } finally {
        try { platformFileSystem.delete(tempFile) } catch (_: Exception) {}
    }
}
