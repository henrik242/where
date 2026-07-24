package no.synth.where.data

import no.synth.kmpzip.io.readBytes
import no.synth.kmpzip.zip.ZipFile

/** A single file pulled out of a zip archive: its base name and raw bytes. */
internal data class ArchiveEntry(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ArchiveEntry && name == other.name && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * Zip extraction for track import, backed by kmp-zip's [ZipFile] (central-directory reader, so it
 * copes with entries whose local header omits sizes). Directories, macOS resource-fork junk,
 * oversized and unreadable entries are skipped. Not ZIP64-aware.
 */
internal object ArchiveExtractor {
    // Declared uncompressed size comes from an untrusted header; skip anything absurd for a track.
    private const val MAX_ENTRY_BYTES = 128L * 1024 * 1024

    /**
     * Extract every entry whose base name satisfies [keep]. Directory entries, macOS resource-fork
     * junk (`__MACOSX/`, `._*`), oversized, unsupported and corrupt entries are silently skipped;
     * an unreadable archive yields an empty list. Order follows the central directory.
     */
    fun extract(data: ByteArray, keep: (String) -> Boolean): List<ArchiveEntry> {
        val zip = runCatching { ZipFile(data) }.getOrNull() ?: return emptyList()
        return zip.use {
            zip.entries.mapNotNull { entry ->
                if (entry.isDirectory || entry.name.startsWith("__MACOSX/")) return@mapNotNull null
                val baseName = entry.name.substringAfterLast('/')
                if (baseName.isEmpty() || baseName.startsWith("._")) return@mapNotNull null
                if (!keep(baseName) || entry.size > MAX_ENTRY_BYTES) return@mapNotNull null
                runCatching {
                    ArchiveEntry(baseName, zip.getInputStream(entry).use { it.readBytes() })
                }.getOrNull()
            }
        }
    }
}
