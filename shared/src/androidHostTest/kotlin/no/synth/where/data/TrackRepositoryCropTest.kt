package no.synth.where.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.data.geo.LatLng
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * In-memory [TrackDao] modelling the tracks + points tables, so the crop overwrite path (which uses
 * the interface's default delete-then-insert `replaceTrackWithPoints`) can be verified without an
 * instrumented Room database. Synchronized because the repository collects [getAllTracks] on a
 * background dispatcher while the test writes.
 */
private class FakeTrackDao : TrackDao {
    private val lock = Any()
    private val tracks = LinkedHashMap<String, TrackEntity>()
    private val rows = ArrayList<TrackPointEntity>()
    private var nextId = 1L
    private val allTracks = MutableStateFlow<List<TrackEntity>>(emptyList())

    fun pointCount(trackId: String): Int = synchronized(lock) { rows.count { it.trackId == trackId } }

    override fun getAllTracks(): Flow<List<TrackEntity>> = allTracks

    override suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity> =
        synchronized(lock) { rows.filter { it.trackId == trackId }.sortedBy { it.orderIndex } }

    override suspend fun insertTrack(track: TrackEntity) = synchronized(lock) {
        tracks[track.id] = track
        allTracks.value = tracks.values.toList()
    }

    override suspend fun insertTrackPoints(points: List<TrackPointEntity>) = synchronized(lock) {
        points.forEach { rows.add(it.copy(id = nextId++)) }
    }

    override suspend fun deleteTrack(trackId: String) = synchronized(lock) {
        tracks.remove(trackId)
        rows.removeAll { it.trackId == trackId }
        allTracks.value = tracks.values.toList()
    }

    override suspend fun deletePointsForTrack(trackId: String) = synchronized(lock) {
        rows.removeAll { it.trackId == trackId }
        Unit
    }

    override suspend fun renameTrack(trackId: String, name: String) = synchronized(lock) {
        tracks[trackId]?.let { tracks[trackId] = it.copy(name = name) }
        Unit
    }

    override suspend fun getAllTracksOnce(): List<TrackEntity> = synchronized(lock) { tracks.values.toList() }
}

class TrackRepositoryCropTest {

    private fun tmpDir(): PlatformFile {
        val dir = File(System.getProperty("java.io.tmpdir"), "crop-repo-${System.nanoTime()}")
        dir.mkdirs()
        return PlatformFile(dir)
    }

    private fun repo(dao: TrackDao = FakeTrackDao()) = TrackRepository(tmpDir(), dao)

    private fun sampleTrack(id: String = "t1", n: Int = 5) = Track(
        id = id,
        name = "t",
        points = (0 until n).map { TrackPoint(LatLng(60.0 + it * 0.01, 10.0), timestamp = it.toLong()) },
        startTime = 0L,
        endTime = (n - 1).toLong(),
    )

    private fun seed(dao: FakeTrackDao, track: Track) = runBlocking {
        dao.insertTrack(TrackEntity(track.id, track.name, track.startTime, track.endTime, false))
        dao.insertTrackPoints(
            track.points.mapIndexed { i, p ->
                TrackPointEntity(0, track.id, p.latLng.latitude, p.latLng.longitude, p.timestamp, p.altitude, p.accuracy, i)
            },
        )
    }

    private fun awaitPointCount(dao: FakeTrackDao, trackId: String, expected: Int) = runBlocking {
        withTimeout(2000) { while (dao.pointCount(trackId) != expected) delay(20) }
    }

    @Test
    fun startCropSeedsFullRangeAndFocuses() {
        val repo = repo()
        val t = sampleTrack()
        repo.addViewingTrack(t)
        repo.startCrop(t.id)
        assertEquals(TrackCropState(t.id, 0, 4), repo.cropState.value)
        assertEquals(t.id, repo.focusedTrackId.value)
    }

    @Test
    fun startCropIgnoresUnknownOrTooShortTrack() {
        val repo = repo()
        repo.startCrop("missing")
        assertNull(repo.cropState.value)
        val one = sampleTrack(id = "one", n = 1)
        repo.addViewingTrack(one)
        repo.startCrop("one")
        assertNull(repo.cropState.value)
    }

    @Test
    fun updateCropClampsToBoundsAndNoOpsWithoutActiveCrop() {
        val repo = repo()
        val t = sampleTrack()
        repo.addViewingTrack(t)
        repo.startCrop(t.id)
        repo.updateCrop(-5, 99)
        assertEquals(TrackCropState(t.id, 0, 4), repo.cropState.value)
        repo.updateCrop(3, 3)
        assertEquals(TrackCropState(t.id, 3, 4), repo.cropState.value)
        repo.cancelCrop()
        repo.updateCrop(1, 2)
        assertNull(repo.cropState.value)
    }

    @Test
    fun applyCropReplacesTrackInPlaceWithoutStrandingPoints() {
        val dao = FakeTrackDao()
        val repo = repo(dao)
        val t = sampleTrack(n = 5)
        seed(dao, t)
        repo.addViewingTrack(t)
        repo.startCrop(t.id)
        repo.updateCrop(1, 3)
        repo.applyCrop()

        // Synchronous state: cropped in the viewing set, crop mode exited, undo offered.
        assertNull(repo.cropState.value)
        assertEquals(3, repo.viewingTracks.value.first { it.id == t.id }.points.size)
        assertNotNull(repo.cropUndo.value)
        // Async DB: exactly the 3 kept points remain — the trimmed 2 are not stranded.
        awaitPointCount(dao, t.id, 3)
    }

    @Test
    fun noOpCropSkipsRewriteAndUndo() {
        val dao = FakeTrackDao()
        val repo = repo(dao)
        val t = sampleTrack(n = 5)
        seed(dao, t)
        repo.addViewingTrack(t)
        repo.startCrop(t.id)          // full range, no handle movement
        repo.applyCrop()
        assertNull(repo.cropState.value)
        assertNull(repo.cropUndo.value)
        assertEquals(5, repo.viewingTracks.value.first { it.id == t.id }.points.size)
    }

    @Test
    fun undoCropRestoresOriginalPoints() {
        val dao = FakeTrackDao()
        val repo = repo(dao)
        val t = sampleTrack(n = 5)
        seed(dao, t)
        repo.addViewingTrack(t)
        repo.startCrop(t.id)
        repo.updateCrop(1, 3)
        repo.applyCrop()
        awaitPointCount(dao, t.id, 3)
        repo.undoCrop()
        assertNull(repo.cropUndo.value)
        assertEquals(5, repo.viewingTracks.value.first { it.id == t.id }.points.size)
        awaitPointCount(dao, t.id, 5)
    }

    @Test
    fun cropResetsWhenItsTrackLeavesOrNavigationStarts() {
        // removing the cropped track clears crop
        repo().apply {
            val t = sampleTrack()
            addViewingTrack(t); startCrop(t.id)
            removeViewingTrack(t.id)
            assertNull(cropState.value)
        }
        // removing a different track keeps crop
        repo().apply {
            val t = sampleTrack("a"); val other = sampleTrack("b")
            addViewingTrack(t); addViewingTrack(other); startCrop(t.id)
            removeViewingTrack(other.id)
            assertNotNull(cropState.value)
        }
        // clearing all viewing tracks clears crop
        repo().apply {
            val t = sampleTrack()
            addViewingTrack(t); startCrop(t.id)
            clearViewingTracks()
            assertNull(cropState.value)
        }
        // starting navigation clears crop
        repo().apply {
            val t = sampleTrack()
            addViewingTrack(t); startCrop(t.id)
            startNavigation(t)
            assertNull(cropState.value)
        }
    }

    @Test
    fun setElevationMarkerRoundTripsAndClears() {
        val repo = repo()
        repo.addViewingTrack(sampleTrack())
        repo.setElevationMarker(3)
        assertEquals(3, repo.elevationMarker.value)
        repo.setElevationMarker(null)
        assertNull(repo.elevationMarker.value)
    }

    @Test
    fun viewChangesClearElevationMarker() {
        fun withMarker(): TrackRepository = repo().apply {
            addViewingTrack(sampleTrack())
            setElevationMarker(2)
        }
        withMarker().apply { setFocusedTrack(null); assertNull(elevationMarker.value) }
        withMarker().apply { toggleFocusedTrack("t1"); assertNull(elevationMarker.value) }
        withMarker().apply { removeViewingTrack("t1"); assertNull(elevationMarker.value) }
        withMarker().apply { clearViewingTracks(); assertNull(elevationMarker.value) }
        withMarker().apply { setViewingTracks(emptyList()); assertNull(elevationMarker.value) }
        withMarker().apply { addViewingTrack(sampleTrack("t2")); assertNull(elevationMarker.value) }
        withMarker().apply { startCrop("t1"); assertNull(elevationMarker.value) }
        withMarker().apply { startNavigation(sampleTrack()); assertNull(elevationMarker.value) }
    }

    @Test
    fun enteringTrackViewEndsNavigation() {
        // Showing a track (or a multi-select set) on the map takes over the view, so it must end an
        // active navigation session rather than leave a half-rendered navigate+view state.
        repo().apply {
            val t = sampleTrack()
            startNavigation(t)
            assertNotNull(navigation.value)
            addViewingTrack(t)
            assertNull(navigation.value)
        }
        repo().apply {
            val t = sampleTrack()
            startNavigation(t)
            setViewingTracks(listOf(t))
            assertNull(navigation.value)
        }
    }

    @Test
    fun tapClearingFocusWhileCroppingKeepsCropEditorUp() {
        // A stray map tap (unfocus) must not tear down the crop editor while a crop is active.
        repo().apply {
            val t = sampleTrack()
            addViewingTrack(t)
            startCrop(t.id)
            setFocusedTrack(null)
            assertEquals(t.id, focusedTrackId.value)
            assertNotNull(cropState.value)
            // The explicit exit (cancelCrop) still clears it, after which unfocus works again.
            cancelCrop()
            setFocusedTrack(null)
            assertNull(focusedTrackId.value)
        }
    }
}
