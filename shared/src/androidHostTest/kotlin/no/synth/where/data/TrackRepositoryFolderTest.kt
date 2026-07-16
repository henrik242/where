package no.synth.where.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.geo.LatLng
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackRepositoryFolderTest {

    private fun tmpDir(): PlatformFile {
        val dir = File(System.getProperty("java.io.tmpdir"), "folder-repo-${System.nanoTime()}")
        dir.mkdirs()
        return PlatformFile(dir)
    }

    private fun repo(dao: TrackDao) = TrackRepository(tmpDir(), dao)

    private fun seed(dao: InMemoryTrackDao, id: String, folder: String? = null) = runBlocking {
        dao.insertTrack(TrackEntity(id, "track-$id", 0L, 1L, false, folder))
    }

    private fun awaitFolder(dao: InMemoryTrackDao, trackId: String, expected: String?) = runBlocking {
        withTimeout(2000) { while (dao.folderOf(trackId) != expected) delay(20) }
    }

    @Test
    fun setTracksFolderTagsTheGivenTracksOnly() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1")
        seed(dao, "t2")
        seed(dao, "t3")
        repo.setTracksFolder(listOf("t1", "t3"), "Skiing")
        awaitFolder(dao, "t1", "Skiing")
        awaitFolder(dao, "t3", "Skiing")
        assertEquals(null, dao.folderOf("t2"))
    }

    @Test
    fun setTracksFolderNormalizesBlankToNullAndTrims() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        repo.setTracksFolder(listOf("t1"), "   ")
        awaitFolder(dao, "t1", null)
        repo.setTracksFolder(listOf("t1"), "  Hiking ")
        awaitFolder(dao, "t1", "Hiking")
    }

    @Test
    fun setTracksFolderWithNullClearsTheFolder() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        repo.setTracksFolder(listOf("t1"), null)
        awaitFolder(dao, "t1", null)
    }

    @Test
    fun renameFolderMovesOnlyMatchingTracks() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        seed(dao, "t2", folder = "Hiking")
        seed(dao, "t3")
        repo.renameFolder("Skiing", "Touring")
        awaitFolder(dao, "t1", "Touring")
        assertEquals("Hiking", dao.folderOf("t2"))
        assertEquals(null, dao.folderOf("t3"))
    }

    @Test
    fun renameFolderToBlankNeverReachesTheDao() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        repo.renameFolder("Skiing", "  ")
        assertEquals(0, dao.renameFolderCalls)
        assertEquals("Skiing", dao.folderOf("t1"))
    }

    @Test
    fun renameFolderTrimsAndMergesIntoExistingFolder() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        seed(dao, "t2", folder = "Hiking")
        repo.renameFolder("Skiing", "  Hiking  ")
        awaitFolder(dao, "t1", "Hiking")
        assertEquals("Hiking", dao.folderOf("t2"))
    }

    @Test
    fun removeFolderClearsFolderButKeepsTracks() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        seed(dao, "t2", folder = "Skiing")
        seed(dao, "t3", folder = "Hiking")
        repo.removeFolder("Skiing")
        awaitFolder(dao, "t1", null)
        awaitFolder(dao, "t2", null)
        assertEquals(3, dao.trackCount())
        assertEquals("Hiking", dao.folderOf("t3"))
    }

    @Test
    fun restoreFoldersPutsEachTrackBackInItsPriorFolder() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        seed(dao, "t1", folder = "Skiing")
        seed(dao, "t2")
        val previous = mapOf("t1" to "Skiing", "t2" to null)
        repo.setTracksFolder(listOf("t1", "t2"), "Hiking")
        awaitFolder(dao, "t1", "Hiking")
        awaitFolder(dao, "t2", "Hiking")
        repo.restoreFolders(previous)
        awaitFolder(dao, "t1", "Skiing")
        awaitFolder(dao, "t2", null)
    }

    @Test
    fun applyCropPreservesFolder() {
        val dao = InMemoryTrackDao()
        val repo = repo(dao)
        val points = (0 until 5).map { TrackPoint(LatLng(60.0 + it * 0.01, 10.0), timestamp = it.toLong()) }
        val track = Track(id = "t1", name = "t", points = points, startTime = 0L, endTime = 4L, folder = "Skiing")
        seed(dao, "t1", folder = "Skiing")
        repo.addViewingTrack(track)
        repo.startCrop("t1")
        repo.updateCrop(1, 3)
        repo.applyCrop()
        runBlocking {
            withTimeout(2000) { while (dao.entity("t1")?.folder != "Skiing" || dao.getPointsForTrack("t1").size != 3) delay(20) }
        }
        assertEquals("Skiing", dao.folderOf("t1"))
    }
}
