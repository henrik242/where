package no.synth.where.ui

import no.synth.where.data.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackFolderSectionsTest {

    private fun track(name: String, folder: String? = null) = Track(
        id = name,
        name = name,
        points = emptyList(),
        startTime = 0L,
        folder = folder,
    )

    @Test
    fun groupsUntaggedFirstThenFoldersCaseInsensitivelySorted() {
        val tracks = listOf(
            track("a", "skiing"),
            track("b"),
            track("c", "Hiking"),
            track("d", "Running"),
        )
        val sections = groupTracksByFolder(tracks)
        assertEquals(listOf(null, "Hiking", "Running", "skiing"), sections.map { it.folder })
        assertEquals(listOf("b"), sections[0].tracks.map { it.name })
    }

    @Test
    fun preservesIncomingOrderWithinSection() {
        val tracks = listOf(
            track("newest", "Skiing"),
            track("older", "Skiing"),
            track("oldest", "Skiing"),
        )
        val sections = groupTracksByFolder(tracks)
        assertEquals(listOf("newest", "older", "oldest"), sections.single().tracks.map { it.name })
    }

    @Test
    fun emptyInputGivesNoSections() {
        assertTrue(groupTracksByFolder(emptyList()).isEmpty())
    }

    @Test
    fun allUntaggedGivesSingleNullSection() {
        val sections = groupTracksByFolder(listOf(track("a"), track("b")))
        assertEquals(1, sections.size)
        assertEquals(null, sections.single().folder)
        assertEquals(2, sections.single().tracks.size)
    }

    @Test
    fun foldersDifferingOnlyByCaseStaySeparate() {
        val tracks = listOf(track("a", "skiing"), track("b", "Skiing"))
        assertEquals(listOf("skiing", "Skiing"), groupTracksByFolder(tracks).mapNotNull { it.folder })
        assertEquals(listOf("skiing", "Skiing"), folderNames(tracks))
    }

    @Test
    fun folderNamesAreDistinctSortedAndExcludeNull() {
        val tracks = listOf(
            track("a", "skiing"),
            track("b", "Hiking"),
            track("c", "skiing"),
            track("d"),
        )
        assertEquals(listOf("Hiking", "skiing"), folderNames(tracks))
    }

    @Test
    fun rowsAreHeaderlessWhenNoNamedFoldersExist() {
        val rows = buildTrackListRows(groupTracksByFolder(listOf(track("a"), track("b"))), emptySet())
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is TrackListRow.Item })
    }

    @Test
    fun untaggedTracksRenderLooseBeforeFolderHeaders() {
        val tracks = listOf(track("a"), track("b", "Skiing"), track("c", "Skiing"))
        val rows = buildTrackListRows(groupTracksByFolder(tracks), emptySet())
        assertEquals(
            listOf(
                TrackListRow.Item(tracks[0]),
                TrackListRow.Header("Skiing", 2),
                TrackListRow.Item(tracks[1]),
                TrackListRow.Item(tracks[2]),
            ),
            rows,
        )
    }

    @Test
    fun collapsedFolderEmitsHeaderOnlyWhileUntaggedStayVisible() {
        val tracks = listOf(track("a"), track("b", "Skiing"), track("c", "Skiing"))
        val rows = buildTrackListRows(groupTracksByFolder(tracks), setOf("Skiing"))
        assertEquals(
            listOf(
                TrackListRow.Item(tracks[0]),
                TrackListRow.Header("Skiing", 2),
            ),
            rows,
        )
    }
}
