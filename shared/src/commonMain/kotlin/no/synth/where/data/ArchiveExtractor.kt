package no.synth.where.data

/** A single file pulled out of a zip archive: its base name and raw bytes. */
internal data class ArchiveEntry(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ArchiveEntry && name == other.name && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * Minimal ZIP reader (pure Kotlin, uses [Inflate] for DEFLATE). Reads the central directory so it
 * copes with entries whose local header omits sizes. Only the STORED and DEFLATE methods are
 * supported; other entries, directories and unreadable entries are skipped. Not ZIP64-aware.
 */
internal object ArchiveExtractor {
    private const val EOCD_SIGNATURE = 0x06054b50L
    private const val CENTRAL_SIGNATURE = 0x02014b50L
    private const val LOCAL_SIGNATURE = 0x04034b50L
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8

    /** True if [data] starts with the local-file-header magic "PK". */
    fun isZip(data: ByteArray): Boolean =
        data.size >= 4 && readU32(data, 0) == LOCAL_SIGNATURE

    /**
     * Extract every entry whose base name satisfies [keep]. Directory entries, macOS resource-fork
     * junk (`__MACOSX/`, `._*`), unsupported methods and corrupt entries are silently skipped.
     * Order follows the central directory.
     */
    fun extract(data: ByteArray, keep: (String) -> Boolean): List<ArchiveEntry> {
        val eocd = findEndOfCentralDirectory(data) ?: return emptyList()
        val entryCount = readU16(data, eocd + 10)
        var pos = readU32(data, eocd + 16).toInt()
        val result = mutableListOf<ArchiveEntry>()
        repeat(entryCount) {
            if (pos < 0 || pos + 46 > data.size || readU32(data, pos) != CENTRAL_SIGNATURE) return result
            val method = readU16(data, pos + 10)
            val compressedSize = readU32(data, pos + 20).toInt()
            val uncompressedSize = readU32(data, pos + 24).toInt()
            val nameLen = readU16(data, pos + 28)
            val extraLen = readU16(data, pos + 30)
            val commentLen = readU16(data, pos + 32)
            val localOffset = readU32(data, pos + 42).toInt()
            if (pos + 46 + nameLen > data.size) return result
            val fullName = data.decodeToString(pos + 46, pos + 46 + nameLen)
            pos += 46 + nameLen + extraLen + commentLen

            if (fullName.endsWith("/")) return@repeat // directory
            if (fullName.startsWith("__MACOSX/")) return@repeat
            val baseName = fullName.substringAfterLast('/')
            if (baseName.isEmpty() || baseName.startsWith("._")) return@repeat
            if (!keep(baseName)) return@repeat

            val bytes = readLocalEntry(data, localOffset, method, compressedSize, uncompressedSize) ?: return@repeat
            result.add(ArchiveEntry(baseName, bytes))
        }
        return result
    }

    private fun readLocalEntry(data: ByteArray, offset: Int, method: Int, compressedSize: Int, uncompressedSize: Int): ByteArray? {
        if (offset < 0 || offset + 30 > data.size || readU32(data, offset) != LOCAL_SIGNATURE) return null
        if (compressedSize < 0 || uncompressedSize < 0) return null // ZIP64 / overflow: unsupported
        val nameLen = readU16(data, offset + 26)
        val extraLen = readU16(data, offset + 28)
        val dataStart = offset + 30 + nameLen + extraLen
        if (dataStart + compressedSize > data.size) return null
        val compressed = data.copyOfRange(dataStart, dataStart + compressedSize)
        return when (method) {
            METHOD_STORED -> compressed
            METHOD_DEFLATE -> runCatching { Inflate.inflate(compressed, uncompressedSize) }.getOrNull()
            else -> null
        }
    }

    // The end-of-central-directory record sits at the tail, after an optional comment (<= 0xFFFF).
    private fun findEndOfCentralDirectory(data: ByteArray): Int? {
        if (data.size < 22) return null
        val earliest = maxOf(0, data.size - 22 - 0xFFFF)
        var i = data.size - 22
        while (i >= earliest) {
            if (readU32(data, i) == EOCD_SIGNATURE) return i
            i--
        }
        return null
    }

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(data: ByteArray, offset: Int): Long =
        (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
}
