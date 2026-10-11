package no.synth.where.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import no.synth.where.data.db.SavedPointDao
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SavedPointsRepositoryImportTest {

    private fun tmpDir(): PlatformFile {
        val dir = File(System.getProperty("java.io.tmpdir"), "point-repo-${System.nanoTime()}")
        dir.mkdirs()
        return PlatformFile(dir)
    }

    private fun repo(dao: SavedPointDao) = SavedPointsRepository(tmpDir(), dao)

    private fun gpx(vararg names: String): ByteArray {
        val waypoints = names.joinToString("\n") {
            """<wpt lat="59.9" lon="10.7"><name>$it</name></wpt>"""
        }
        return """<gpx version="1.1">$waypoints</gpx>""".encodeToByteArray()
    }

    @Test
    fun readPointsFindsEveryWaypointInEveryFile() {
        val candidates = runBlocking {
            repo(InMemorySavedPointDao()).readPoints(listOf(gpx("One", "Two"), gpx("Three")))
        }

        assertEquals(listOf("One", "Two", "Three"), candidates.waypoints.map { it.name })
        assertEquals(0, candidates.failedCount)
    }

    @Test
    fun readPointsStoresNothingUntilTheUserPicks() {
        val dao = InMemorySavedPointDao()
        runBlocking { repo(dao).readPoints(listOf(gpx("One", "Two"))) }

        assertTrue(dao.all().isEmpty())
    }

    @Test
    fun readPointsCountsFilesWithoutWaypointsAsFailures() {
        val trackOnly = """
            <gpx version="1.1"><trk><name>A hike</name><trkseg>
            <trkpt lat="60.0" lon="10.0"/>
            </trkseg></trk></gpx>
        """.trimIndent().encodeToByteArray()
        val candidates = runBlocking {
            repo(InMemorySavedPointDao()).readPoints(listOf(gpx("Good"), trackOnly, byteArrayOf(1, 2, 3)))
        }

        assertEquals(listOf("Good"), candidates.waypoints.map { it.name })
        assertEquals(2, candidates.failedCount)
    }

    @Test
    fun readPointsReadsGpxEntriesOutOfAZip() {
        // tripZip holds two track-only .gpx files, so the archive is opened but yields no waypoints.
        val candidates = runBlocking { repo(InMemorySavedPointDao()).readPoints(listOf(TestFixtures.tripZip)) }

        assertTrue(candidates.waypoints.isEmpty())
        assertEquals(2, candidates.failedCount)
    }

    @Test
    fun importPointsStoresOnlyTheWaypointsItIsGiven() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)
        val candidates = runBlocking { r.readPoints(listOf(gpx("Keep", "Drop", "Also keep"))) }
        val picked = candidates.waypoints.filter { it.name != "Drop" }
        val imported = runBlocking { r.importPoints(picked) }

        assertEquals(2, imported.size)
        assertEquals(listOf("Keep", "Also keep"), dao.all().map { it.name })
    }

    @Test
    fun importPointsMakesNamesUniqueWithinTheBatch() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)
        val candidates = runBlocking { r.readPoints(listOf(gpx("Same"), gpx("Same"))) }
        runBlocking { r.importPoints(candidates.waypoints) }

        assertEquals(setOf("Same", "Same (2)"), dao.all().map { it.name }.toSet())
    }

    @Test
    fun importPointsMakesNamesUniqueAgainstAlreadyStoredPoints() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)
        runBlocking {
            r.importPoints(r.readPoints(listOf(gpx("Same"))).waypoints)
            r.savedPoints.first { it.size == 1 }          // let the DAO flow reach the repository
            r.importPoints(r.readPoints(listOf(gpx("Same"))).waypoints)
        }

        assertEquals(listOf("Same", "Same (2)"), dao.all().map { it.name })
    }

    @Test
    fun importPointsKeepsCoordinatesDescriptionAndTime() {
        val dao = InMemorySavedPointDao()
        val r = repo(dao)
        val gpx = """
            <gpx version="1.1"><wpt lon="10.897" lat="59.9202">
            <ele>342</ele><name>59-Puttåsen</name><desc>Toppen</desc><time>2020-07-09T11:39:53Z</time>
            </wpt></gpx>
        """.trimIndent().encodeToByteArray()
        runBlocking { r.importPoints(r.readPoints(listOf(gpx)).waypoints) }

        val stored = dao.all().single()
        assertEquals("59-Puttåsen", stored.name)
        assertEquals(59.9202, stored.latitude)
        assertEquals(10.897, stored.longitude)
        assertEquals("Toppen", stored.description)
        assertEquals(1594294793000L, stored.timestamp)
    }
}
