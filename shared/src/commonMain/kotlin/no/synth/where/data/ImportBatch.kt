package no.synth.where.data

/** A file chosen in the import picker: its display name and raw bytes. Platform pickers produce these. */
data class PickedFile(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is PickedFile && name == other.name && bytes.contentEquals(other.bytes))
    override fun hashCode(): Int = 31 * name.hashCode() + bytes.contentHashCode()
}

/**
 * A bulk import waiting for the user to pick a destination folder. Carries the picked file bytes so
 * platform screens hold one piece of state, not two. [suggestedFolder] is prefilled in the picker
 * (the zip's name for a single archive); null means loose multi-select, so it opens on "No folder".
 */
data class PendingBulkImport(val items: List<ByteArray>, val suggestedFolder: String?)

/** What the UI should tell the user after a bulk import finishes. */
enum class BulkImportOutcome { IMPORTED, NONE_FOUND, ALL_FAILED }

/**
 * IMPORTED when at least one track landed (the snackbar then reports the count, partial or not);
 * NONE_FOUND when the selection held nothing importable (e.g. a zip with no .gpx/.fit); ALL_FAILED
 * when every file present failed to parse.
 */
fun BulkImportResult.outcome(): BulkImportOutcome = when {
    importedCount > 0 -> BulkImportOutcome.IMPORTED
    totalCount == 0 -> BulkImportOutcome.NONE_FOUND
    else -> BulkImportOutcome.ALL_FAILED
}

/** True for the file extensions the track parser understands. */
fun isTrackFileName(name: String): Boolean =
    name.endsWith(".gpx", ignoreCase = true) || name.endsWith(".fit", ignoreCase = true)

/**
 * A pick is a "bulk" import (folder prompt + batch progress) when it is more than one file, or a
 * single zip archive. A single plain track file keeps the simpler one-shot import path.
 */
fun isBulkImport(files: List<PickedFile>): Boolean =
    files.size > 1 || (files.size == 1 && ArchiveExtractor.isZip(files[0].bytes))

/**
 * Folder name to prefill in the import prompt: the archive's base name when the pick is a single
 * zip (e.g. "norway-trip.zip" -> "norway-trip"), otherwise null (loose multi-select has no natural
 * name, so the prompt opens on "No folder").
 */
fun suggestedImportFolder(files: List<PickedFile>): String? {
    val only = files.singleOrNull() ?: return null
    if (!ArchiveExtractor.isZip(only.bytes)) return null
    val base = only.name.substringAfterLast('/').substringAfterLast('\\')
    val withoutExtension = if (base.endsWith(".zip", ignoreCase = true)) base.dropLast(4) else base
    return withoutExtension.trim().ifEmpty { null }
}
