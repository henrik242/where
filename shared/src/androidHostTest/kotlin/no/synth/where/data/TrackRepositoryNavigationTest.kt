package no.synth.where.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import no.synth.where.data.db.TrackDao
import no.synth.where.data.db.TrackEntity
import no.synth.where.data.db.TrackPointEntity
import no.synth.where.data.geo.LatLng
import no.synth.where.data.navigation.NavigationProgress
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** No-op [TrackDao]: the navigation session and its progress never touch the database. */
private class NoopTrackDao : TrackDao {
    override fun getAllTracks(): Flow<List<TrackEntity>> = MutableStateFlow(emptyList())
    override suspend fun getPointsForTrack(trackId: String): List<TrackPointEntity> = emptyList()
    override suspend fun insertTrack(track: TrackEntity) {}
    override suspend fun insertTrackPoints(points: List<TrackPointEntity>) {}
    override suspend fun deleteTrack(trackId: String) {}
    override suspend fun deletePointsForTrack(trackId: String) {}
    override suspend fun renameTrack(trackId: String, name: String) {}
    override suspend fun getAllTracksOnce(): List<TrackEntity> = emptyList()
}

class TrackRepositoryNavigationTest {

    private fun repo(): TrackRepository {
        val dir = File(System.getProperty("java.io.tmpdir"), "nav-repo-${System.nanoTime()}")
        dir.mkdirs()
        return TrackRepository(PlatformFile(dir), NoopTrackDao())
    }

    private fun track(id: String = "t1") = Track(
        id = id,
        name = "t",
        points = (0 until 3).map { TrackPoint(LatLng(60.0 + it * 0.01, 10.0), timestamp = it.toLong()) },
        startTime = 0L,
        isRecording = false,
    )

    private fun progress() = NavigationProgress(
        onCourse = true,
        offCourseMeters = 0.0,
        snapped = LatLng(60.0, 10.0),
        segment = 0,
        location = LatLng(60.0, 10.0),
        remainingMeters = 100.0,
        remainingAscent = null,
        remainingDescent = null,
        atEnd = false,
    )

    @Test
    fun startNavigationWhileRecordingIsNoOp() {
        val repo = repo()
        repo.startNewTrack()
        repo.startNavigation(track())
        assertNull(repo.navigation.value)
    }

    @Test
    fun updateNavigationProgressPublishesWhileNavigating() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        assertNotNull(repo.navigationProgress.value)
    }

    @Test
    fun updateWithoutSessionIsDropped() {
        val repo = repo()
        repo.updateNavigationProgress(progress())
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun stopNavigationClearsSessionAndProgress() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.stopNavigation()
        assertNull(repo.navigation.value)
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun toggleReverseClearsProgressButKeepsSession() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.toggleNavigationReverse()
        assertEquals(true, repo.navigation.value?.reversed)
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun enteringTrackViewEndsNavigationAndClearsProgress() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.addViewingTrack(track("t2"))
        assertNull(repo.navigation.value)
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun replacingViewingSetEndsNavigationAndClearsProgress() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.setViewingTracks(listOf(track("t2")))
        assertNull(repo.navigation.value)
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun restartingNavigationClearsPreviousProgress() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.startNavigation(track("t2"))
        assertNull(repo.navigationProgress.value)
    }
}
