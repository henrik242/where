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
    override suspend fun updateFolderForTracks(trackIds: List<String>, folder: String?) {}
    override suspend fun renameFolder(oldName: String, newName: String) {}
    override suspend fun clearFolder(folderName: String) {}
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

    /** A track with altitudes, so [hasElevationData] is true and the navigation chart can open. */
    private fun elevatedTrack(id: String = "t1") = Track(
        id = id,
        name = "t",
        points = (0 until 3).map {
            TrackPoint(LatLng(60.0 + it * 0.01, 10.0), timestamp = it.toLong(), altitude = 100.0 + it * 10)
        },
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
    fun stopNavigationShowsNavigatedTrackInDetailMode() {
        val repo = repo()
        repo.startNavigation(track("nav"))
        assertEquals(emptyList(), repo.viewingTracks.value.map { it.id })   // dropped while navigating
        repo.stopNavigation()
        assertEquals(listOf("nav"), repo.viewingTracks.value.map { it.id }) // back on the map
        assertEquals("nav", repo.focusedTrackId.value)                      // and focused (detail mode)
    }

    @Test
    fun stopNavigationKeepsOtherViewingTracksAndFocusesTheNavigatedOne() {
        val repo = repo()
        repo.setViewingTracks(listOf(track("nav"), track("other")))
        repo.startNavigation(track("nav"))
        assertEquals(listOf("other"), repo.viewingTracks.value.map { it.id })
        repo.stopNavigation()
        assertEquals(listOf("other", "nav"), repo.viewingTracks.value.map { it.id })
        assertEquals("nav", repo.focusedTrackId.value)
    }

    @Test
    fun toggleReverseRecomputesProgressFromLastFixAndKeepsSession() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.toggleNavigationReverse()
        assertEquals(true, repo.navigation.value?.reversed)
        // Recomputed against the last known location for the new direction, not nulled: a stationary
        // user gets no fresh fix, so nulling would strand the card on "locating" (the u-turn bug).
        // The last fix (60.0, 10.0) is the forward start, i.e. the end once reversed, so a correct
        // recompute lands at the finish - proving it ran against the reversed track, not republished
        // the stale forward snapshot (which had remainingMeters = 100.0, atEnd = false).
        val recomputed = repo.navigationProgress.value
        assertNotNull(recomputed)
        assertEquals(true, recomputed.atEnd)
        assertEquals(0.0, recomputed.remainingMeters, 1.0)
    }

    @Test
    fun toggleReverseWithoutAnyFixLeavesProgressNull() {
        val repo = repo()
        repo.startNavigation(track())   // no fix yet, so nothing to reverse from
        repo.toggleNavigationReverse()
        assertEquals(true, repo.navigation.value?.reversed)
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun addingViewingTrackWhileNavigatingKeepsNavigationAndShowsBoth() {
        val repo = repo()
        repo.startNavigation(track("nav"))
        repo.updateNavigationProgress(progress())
        repo.addViewingTrack(track("other"))
        assertNotNull(repo.navigation.value)
        assertNotNull(repo.navigationProgress.value)
        assertEquals(listOf("other"), repo.viewingTracks.value.map { it.id })
        assertNull(repo.focusedTrackId.value)   // no focus while navigating
    }

    @Test
    fun addingTheNavigatedTrackToViewingIsIgnored() {
        val repo = repo()
        repo.startNavigation(track("nav"))
        repo.addViewingTrack(track("nav"))
        assertEquals(emptyList(), repo.viewingTracks.value.map { it.id })
    }

    @Test
    fun settingViewingTracksWhileNavigatingKeepsNavigationAndDropsNavigatedTrack() {
        val repo = repo()
        repo.startNavigation(track("nav"))
        repo.updateNavigationProgress(progress())
        repo.setViewingTracks(listOf(track("nav"), track("other")))
        assertNotNull(repo.navigation.value)
        assertNotNull(repo.navigationProgress.value)
        assertEquals(listOf("other"), repo.viewingTracks.value.map { it.id })
    }

    @Test
    fun tappingAnotherTrackWhileNavigatingDoesNotFocusIt() {
        val repo = repo()
        repo.startNavigation(track("nav"))
        repo.addViewingTrack(track("other"))
        repo.toggleFocusedTrack("other")
        assertNull(repo.focusedTrackId.value)
    }

    @Test
    fun startNavigationKeepsOtherViewingTracksButDropsTheNavigatedOne() {
        val repo = repo()
        repo.setViewingTracks(listOf(track("nav"), track("other")))
        repo.startNavigation(track("nav"))
        assertNotNull(repo.navigation.value)
        assertEquals(listOf("other"), repo.viewingTracks.value.map { it.id })
        assertNull(repo.focusedTrackId.value)
    }

    @Test
    fun restartingNavigationClearsPreviousProgress() {
        val repo = repo()
        repo.startNavigation(track())
        repo.updateNavigationProgress(progress())
        repo.startNavigation(track("t2"))
        assertNull(repo.navigationProgress.value)
    }

    @Test
    fun toggleNavigationChartFlipsVisibilityAndClearsMarker() {
        val repo = repo()
        repo.startNavigation(elevatedTrack())
        repo.setElevationMarker(1)
        repo.toggleNavigationChart()
        assertEquals(true, repo.navigationChartVisible.value)
        assertNull(repo.elevationMarker.value)
        repo.toggleNavigationChart()
        assertEquals(false, repo.navigationChartVisible.value)
    }

    @Test
    fun toggleNavigationChartIsNoOpWhenIdle() {
        val repo = repo()
        repo.toggleNavigationChart()
        assertEquals(false, repo.navigationChartVisible.value)
    }

    @Test
    fun toggleNavigationChartIsNoOpWithoutElevationData() {
        val repo = repo()
        repo.startNavigation(track())   // track() carries no altitudes
        repo.toggleNavigationChart()
        assertEquals(false, repo.navigationChartVisible.value)
    }

    @Test
    fun hideNavigationChartClosesChartAndIsNoOpWhenAlreadyHidden() {
        val repo = repo()
        repo.startNavigation(elevatedTrack())
        repo.toggleNavigationChart()
        repo.setElevationMarker(1)
        repo.hideNavigationChart()
        assertEquals(false, repo.navigationChartVisible.value)
        assertNull(repo.elevationMarker.value)
        repo.hideNavigationChart()   // already hidden: no-op
        assertEquals(false, repo.navigationChartVisible.value)
    }

    @Test
    fun startAndStopNavigationHideTheChart() {
        val repo = repo()
        repo.startNavigation(elevatedTrack())
        repo.toggleNavigationChart()
        assertEquals(true, repo.navigationChartVisible.value)
        repo.stopNavigation()
        assertEquals(false, repo.navigationChartVisible.value)
        repo.startNavigation(elevatedTrack("t2"))
        assertEquals(false, repo.navigationChartVisible.value)   // a fresh session starts hidden
    }

    @Test
    fun toggleReverseKeepsChartOpenButClearsMarker() {
        val repo = repo()
        repo.startNavigation(elevatedTrack())
        repo.toggleNavigationChart()
        repo.setElevationMarker(1)
        repo.toggleNavigationReverse()
        assertEquals(true, repo.navigationChartVisible.value)   // chart stays across a reverse
        assertNull(repo.elevationMarker.value)                  // marker index is stale, so cleared
    }

    @Test
    fun onTrackTappedTogglesChartForRouteAndFocusesOtherTracks() {
        val repo = repo()
        repo.setViewingTracks(listOf(track("other")))
        repo.startNavigation(elevatedTrack("nav"))
        repo.onTrackTapped("nav")                 // the route -> toggle its chart
        assertEquals(true, repo.navigationChartVisible.value)
        repo.onTrackTapped("other")               // another track -> focus, suppressed while navigating
        assertNull(repo.focusedTrackId.value)
    }

    @Test
    fun onMapTapOutsideTracksHidesChartWhileNavigatingElseClearsFocus() {
        repo().apply {
            startNavigation(elevatedTrack())
            toggleNavigationChart()
            onMapTapOutsideTracks()
            assertEquals(false, navigationChartVisible.value)
        }
        repo().apply {
            addViewingTrack(track("a"))           // focuses "a" (not navigating)
            assertEquals("a", focusedTrackId.value)
            onMapTapOutsideTracks()
            assertNull(focusedTrackId.value)
        }
    }

    @Test
    fun startNavigationByIdStartsAViewedTrackAndReportsSuccess() {
        val repo = repo()
        repo.setViewingTracks(listOf(track("a")))
        assertEquals(true, repo.startNavigationById("a"))
        assertEquals("a", repo.navigation.value?.track?.id)
        assertEquals(false, repo.startNavigationById("missing"))
    }
}
