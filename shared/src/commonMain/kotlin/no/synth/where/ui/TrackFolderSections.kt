package no.synth.where.ui

import no.synth.where.data.Track

/** One collapsible section of the tracks list; [folder] == null holds the tracks with no folder. */
data class FolderSection(val folder: String?, val tracks: List<Track>)

/**
 * Groups tracks into sections: no-folder first (new recordings and imports land there, so they must
 * not be buried below the folders), then named folders sorted case-insensitively. Track order
 * within a section preserves the incoming order.
 *
 * Folders are identified by their exact name, so "Hiking" and "hiking" are two separate folders;
 * only the display order is case-insensitive.
 */
fun groupTracksByFolder(tracks: List<Track>): List<FolderSection> {
    val byFolder = tracks.groupBy { it.folder }
    val named = byFolder.keys.filterNotNull().sortedBy { it.lowercase() }
    return buildList {
        byFolder[null]?.let { add(FolderSection(null, it)) }
        named.forEach { add(FolderSection(it, byFolder.getValue(it))) }
    }
}

/** Distinct folder names, sorted case-insensitively — feeds the folder picker. */
fun folderNames(tracks: List<Track>): List<String> =
    tracks.mapNotNull { it.folder }.distinct().sortedBy { it.lowercase() }

sealed interface TrackListRow {
    data class Header(val folder: String, val count: Int) : TrackListRow
    data class Item(val track: Track) : TrackListRow
}

/**
 * Flattens sections into LazyColumn rows. Untagged tracks render loose at the top with no header;
 * only named folders get a collapsible header, and a collapsed one emits just its header.
 */
fun buildTrackListRows(sections: List<FolderSection>, collapsedFolders: Set<String>): List<TrackListRow> =
    buildList {
        sections.forEach { section ->
            val folder = section.folder
            if (folder == null) {
                section.tracks.forEach { add(TrackListRow.Item(it)) }
            } else {
                add(TrackListRow.Header(folder, section.tracks.size))
                if (folder !in collapsedFolders) {
                    section.tracks.forEach { add(TrackListRow.Item(it)) }
                }
            }
        }
    }
