package no.synth.where.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportBatchTest {

    private fun gpx(name: String) = PickedFile(name, TestFixtures.trackGpx.encodeToByteArray())
    private fun zip(name: String) = PickedFile(name, TestFixtures.tripZip)

    @Test
    fun isTrackFileNameAcceptsGpxAndFitCaseInsensitively() {
        assertTrue(isTrackFileName("route.gpx"))
        assertTrue(isTrackFileName("RIDE.FIT"))
        assertTrue(isTrackFileName("a.GpX"))
        assertFalse(isTrackFileName("notes.txt"))
        assertFalse(isTrackFileName("archive.zip"))
    }

    @Test
    fun singlePlainFileIsNotBulk() {
        assertFalse(isBulkImport(listOf(gpx("a.gpx"))))
    }

    @Test
    fun severalFilesAreBulk() {
        assertTrue(isBulkImport(listOf(gpx("a.gpx"), gpx("b.gpx"))))
    }

    @Test
    fun singleZipIsBulk() {
        assertTrue(isBulkImport(listOf(zip("trip.zip"))))
    }

    @Test
    fun suggestedFolderIsTheZipBaseNameForASingleArchive() {
        assertEquals("norway-trip", suggestedImportFolder(listOf(PickedFile("norway-trip.zip", TestFixtures.tripZip))))
        assertEquals("trip", suggestedImportFolder(listOf(PickedFile("/some/path/trip.ZIP", TestFixtures.tripZip))))
    }

    @Test
    fun noFolderSuggestionForLooseFilesOrMultipleZips() {
        assertNull(suggestedImportFolder(listOf(gpx("a.gpx"), gpx("b.gpx"))))
        assertNull(suggestedImportFolder(listOf(gpx("a.gpx"))))
        assertNull(suggestedImportFolder(listOf(zip("one.zip"), zip("two.zip"))))
    }

    private fun track() = Track(name = "t", points = emptyList(), startTime = 0L)

    @Test
    fun outcomeIsImportedWhenAnyTrackLandedEvenPartially() {
        assertEquals(BulkImportOutcome.IMPORTED, BulkImportResult(listOf(track()), failedCount = 0).outcome())
        assertEquals(BulkImportOutcome.IMPORTED, BulkImportResult(listOf(track()), failedCount = 3).outcome())
    }

    @Test
    fun outcomeIsNoneFoundWhenNothingWasImportable() {
        assertEquals(BulkImportOutcome.NONE_FOUND, BulkImportResult(emptyList(), failedCount = 0).outcome())
    }

    @Test
    fun outcomeIsAllFailedWhenEveryPresentFileFailed() {
        assertEquals(BulkImportOutcome.ALL_FAILED, BulkImportResult(emptyList(), failedCount = 2).outcome())
    }
}
