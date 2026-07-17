package no.synth.where.data

import kotlinx.coroutines.runBlocking
import no.synth.where.data.db.TrackDao
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackRepositoryImportTest {

    private fun tmpDir(): PlatformFile {
        val dir = File(System.getProperty("java.io.tmpdir"), "import-repo-${System.nanoTime()}")
        dir.mkdirs()
        return PlatformFile(dir)
    }

    private fun repo(dao: TrackDao) = TrackRepository(tmpDir(), dao)

    private fun gpx(name: String): ByteArray = """
        <?xml version="1.0"?>
        <gpx version="1.1"><trk><name>$name</name><trkseg>
        <trkpt lat="60.0" lon="10.0"><time>2020-01-01T10:00:00Z</time></trkpt>
        <trkpt lat="60.1" lon="10.1"><time>2020-01-01T10:05:00Z</time></trkpt>
        </trkseg></trk></gpx>
    """.trimIndent().encodeToByteArray()

    @Test
    fun importTracksFilesEveryTrackUnderTheChosenFolder() {
        val dao = InMemoryTrackDao()
        val result = runBlocking { repo(dao).importTracks(listOf(gpx("One"), gpx("Two")), "Trip") }

        assertEquals(2, result.importedCount)
        assertEquals(0, result.failedCount)
        val stored = runBlocking { dao.getAllTracksOnce() }
        assertEquals(setOf("One", "Two"), stored.map { it.name }.toSet())
        assertTrue(stored.all { it.folder == "Trip" })
    }

    @Test
    fun importTracksExpandsAZipIntoItsTrackEntries() {
        val dao = InMemoryTrackDao()
        val result = runBlocking { repo(dao).importTracks(listOf(TestFixtures.tripZip), "Archive") }

        assertEquals(2, result.importedCount) // a.gpx + b.gpx; readme.txt ignored
        val stored = runBlocking { dao.getAllTracksOnce() }
        assertEquals(setOf("Alpha", "Beta"), stored.map { it.name }.toSet())
        assertTrue(stored.all { it.folder == "Archive" })
    }

    @Test
    fun importTracksCountsUnparseableFilesAsFailures() {
        val dao = InMemoryTrackDao()
        val result = runBlocking { repo(dao).importTracks(listOf(gpx("Good"), byteArrayOf(1, 2, 3)), null) }

        assertEquals(1, result.importedCount)
        assertEquals(1, result.failedCount)
        assertEquals(2, result.totalCount)
        val stored = runBlocking { dao.getAllTracksOnce() }
        assertEquals(listOf("Good"), stored.map { it.name })
        assertEquals(null, stored.single().folder)
    }

    @Test
    fun importTracksMakesNamesUniqueWithinTheBatch() {
        val dao = InMemoryTrackDao()
        runBlocking { repo(dao).importTracks(listOf(gpx("Same"), gpx("Same")), null) }

        val stored = runBlocking { dao.getAllTracksOnce() }
        assertEquals(setOf("Same", "Same (2)"), stored.map { it.name }.toSet())
    }

    @Test
    fun importTracksNormalizesABlankFolderToUnfiled() {
        val dao = InMemoryTrackDao()
        runBlocking { repo(dao).importTracks(listOf(gpx("Loose")), "   ") }

        val stored = runBlocking { dao.getAllTracksOnce() }
        assertEquals(null, stored.single().folder)
    }

    @Test
    fun importTracksParsesFitFiles() {
        val dao = InMemoryTrackDao()
        val result = runBlocking { repo(dao).importTracks(listOf(TestFixtures.activityFit), "Rides") }

        assertEquals(1, result.importedCount)
        assertTrue(result.imported.single().points.isNotEmpty())
        assertEquals("Rides", runBlocking { dao.getAllTracksOnce() }.single().folder)
    }

    @Test
    fun importTracksOfAZipWithNothingImportableReportsZeroWithoutFailures() {
        val dao = InMemoryTrackDao()
        val truncated = TestFixtures.tripZip.copyOfRange(0, 60) // valid zip magic, no central directory
        val result = runBlocking { repo(dao).importTracks(listOf(truncated), "Trip") }

        assertEquals(0, result.importedCount)
        assertEquals(0, result.failedCount)
        assertTrue(runBlocking { dao.getAllTracksOnce() }.isEmpty())
    }
}
