package no.synth.where.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import no.synth.where.data.geo.LatLng
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeTrackingSession(
    val onSendPoint: (LatLng) -> Unit,
) : TrackingSession {
    var startedTrackName: String? = null
    var syncedTrack: Track? = null
    var stopped: Boolean = false
    var closed: Boolean = false
    val sharedPoints = mutableListOf<SharedPoint>()
    val deletedPointIds = mutableListOf<String>()

    override fun startTrack(trackName: String) {
        startedTrackName = trackName
    }

    override fun syncExistingTrack(track: Track) {
        syncedTrack = track
    }

    override fun sendPoint(latLng: LatLng, altitude: Double?, accuracy: Float?) {
        onSendPoint(latLng)
    }

    override fun stopTrack() {
        stopped = true
    }

    override fun close() {
        closed = true
    }

    override fun shareSharedPoint(point: SharedPoint) {
        sharedPoints += point
    }

    override fun deleteSharedPoint(pointId: String) {
        deletedPointIds += pointId
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineTrackingCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var scope: TestScope

    private lateinit var isRecording: MutableStateFlow<Boolean>
    private lateinit var liveShareUntilMillis: MutableStateFlow<Long>
    private lateinit var onlineTrackingEnabled: MutableStateFlow<Boolean>
    private lateinit var offlineModeEnabled: MutableStateFlow<Boolean>
    private lateinit var trackingServerUrl: MutableStateFlow<String>
    private lateinit var currentTrack: MutableStateFlow<Track?>

    private var now = 1_000_000L
    private val createdSessions = mutableListOf<FakeTrackingSession>()
    private val sentPoints = mutableListOf<LatLng>()

    private lateinit var coordinator: OnlineTrackingCoordinator

    @BeforeTest
    fun setUp() {
        scope = TestScope(dispatcher)
        isRecording = MutableStateFlow(false)
        liveShareUntilMillis = MutableStateFlow(0L)
        onlineTrackingEnabled = MutableStateFlow(false)
        offlineModeEnabled = MutableStateFlow(false)
        trackingServerUrl = MutableStateFlow("https://test")
        currentTrack = MutableStateFlow(null)
        now = 1_000_000L
        createdSessions.clear()
        sentPoints.clear()

        coordinator = OnlineTrackingCoordinator(
            sources = OnlineTrackingCoordinator.Sources(
                isRecording = isRecording,
                liveShareUntilMillis = liveShareUntilMillis,
                onlineTrackingEnabled = onlineTrackingEnabled,
                offlineModeEnabled = offlineModeEnabled,
                trackingServerUrl = trackingServerUrl,
                currentTrack = currentTrack,
                onViewerCountChanged = {},
            ),
            getClientId = { "abc123" },
            trackingHint = "hint",
            parentScope = scope,
            clock = { now },
            sessionFactory = {
                FakeTrackingSession(onSendPoint = { sentPoints += it })
                    .also { createdSessions += it }
            },
        )
        coordinator.start()
    }

    @AfterTest
    fun tearDown() {
        coordinator.close()
        scope.cancel()
    }

    private fun enterLive() {
        onlineTrackingEnabled.value = true
        liveShareUntilMillis.value = now + 60_000L
    }

    private fun enterRecording(name: String = "Track") {
        onlineTrackingEnabled.value = true
        currentTrack.value = Track(
            name = name,
            points = emptyList(),
            startTime = now,
            isRecording = true,
        )
        isRecording.value = true
    }

    @Test
    fun startsInNoneMode() = runTest(dispatcher) {
        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
        assertEquals(false, coordinator.shouldTrackLocation.value)
        assertEquals(false, coordinator.isLiveSharing.value)
        assertTrue(createdSessions.isEmpty())
    }

    @Test
    fun shareOnlyTransitionsToLive() = runTest(dispatcher) {
        enterLive()

        assertEquals(OnlineTrackingCoordinator.Mode.LIVE, coordinator.mode.value)
        assertEquals(true, coordinator.shouldTrackLocation.value)
        assertEquals(true, coordinator.isLiveSharing.value)
        assertEquals(1, createdSessions.size)
        val name = requireNotNull(createdSessions[0].startedTrackName)
        assertTrue(name.startsWith("Live"), "expected name to start with Live: $name")
    }

    @Test
    fun recordingTransitionsToRecording() = runTest(dispatcher) {
        enterRecording(name = "Hike")

        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)
        assertEquals(true, coordinator.shouldTrackLocation.value)
        assertEquals(false, coordinator.isLiveSharing.value)
        assertEquals(1, createdSessions.size)
        assertEquals("Hike", createdSessions[0].startedTrackName)
    }

    @Test
    fun recordingPreemptsShareOnly() = runTest(dispatcher) {
        enterLive()
        assertEquals(OnlineTrackingCoordinator.Mode.LIVE, coordinator.mode.value)
        val liveSession = createdSessions[0]

        enterRecording()

        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)
        assertTrue(liveSession.stopped, "live session must stop on transition")
        assertTrue(liveSession.closed, "live session must close on transition")
        assertEquals(2, createdSessions.size, "a fresh session is built for RECORDING")
    }

    @Test
    fun recordingStopWithShareActiveSwitchesBackToLive() = runTest(dispatcher) {
        enterLive()
        enterRecording()
        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)
        val recordingSession = createdSessions[1]

        isRecording.value = false
        currentTrack.value = null

        assertEquals(OnlineTrackingCoordinator.Mode.LIVE, coordinator.mode.value)
        assertTrue(recordingSession.stopped)
        assertTrue(recordingSession.closed)
        assertEquals(3, createdSessions.size)
    }

    @Test
    fun expiryTearsDownClient() = runTest(dispatcher) {
        enterLive()
        val liveSession = createdSessions[0]

        // Simulate the deadline auto-expiring (UserPreferences clears the value)
        liveShareUntilMillis.value = 0L

        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
        assertEquals(false, coordinator.shouldTrackLocation.value)
        assertTrue(liveSession.stopped)
        assertTrue(liveSession.closed)
    }

    @Test
    fun onlineTrackingOffWhileRecordingDropsClient() = runTest(dispatcher) {
        enterRecording()
        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)

        onlineTrackingEnabled.value = false

        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
        // shouldTrackLocation stays true because recording is still on (the
        // platform still wants location fixes for the local track).
        assertEquals(true, coordinator.shouldTrackLocation.value)
        assertTrue(createdSessions[0].stopped)
    }

    @Test
    fun offlineModeWhileSharingDropsClient() = runTest(dispatcher) {
        enterLive()
        assertEquals(OnlineTrackingCoordinator.Mode.LIVE, coordinator.mode.value)

        offlineModeEnabled.value = true

        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
        assertEquals(false, coordinator.isLiveSharing.value)
    }

    /**
     * Offline mode drops the session, and the fix stream with it: holding the GPS on for a share
     * that provably cannot upload is pure battery burn. Turning offline mode off resumes both.
     */
    @Test
    fun offlineModeWhileSharingStopsTrackingAndResumesOnDisable() = runTest(dispatcher) {
        enterLive()
        assertEquals(true, coordinator.shouldTrackLocation.value)

        offlineModeEnabled.value = true

        assertEquals(false, coordinator.shouldTrackLocation.value)

        offlineModeEnabled.value = false

        assertEquals(OnlineTrackingCoordinator.Mode.LIVE, coordinator.mode.value)
        assertEquals(true, coordinator.shouldTrackLocation.value)
        assertEquals(2, createdSessions.size, "a fresh session is built when uploads resume")
    }

    @Test
    fun sendPointForwardsWhileLive() = runTest(dispatcher) {
        enterLive()

        coordinator.sendPoint(LatLng(60.0, 10.0), null, null)

        assertEquals(1, sentPoints.size)
        assertEquals(60.0, sentPoints[0].latitude)
    }

    @Test
    fun sendPointDropsAfterDeadline() = runTest(dispatcher) {
        onlineTrackingEnabled.value = true
        liveShareUntilMillis.value = now + 1_000L

        // Advance the clock past the deadline; the StateFlow hasn't been
        // cleared by a UserPreferences expiry job in this test, so the
        // coordinator's synchronous deadline check is the only safety net.
        now += 5_000L

        coordinator.sendPoint(LatLng(60.0, 10.0), null, null)
        assertTrue(sentPoints.isEmpty(), "sendPoint must drop fixes after the deadline")
    }

    @Test
    fun sendPointForwardsWhileRecordingPastDeadline() = runTest(dispatcher) {
        enterRecording()

        coordinator.sendPoint(LatLng(60.0, 10.0), null, null)
        assertEquals(1, sentPoints.size)
    }

    @Test
    fun closeTearsDownEverything() = runTest(dispatcher) {
        enterLive()
        val session = createdSessions[0]

        coordinator.close()

        assertTrue(session.stopped)
        assertTrue(session.closed)
        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
    }

    @Test
    fun addSharedPointWhileLivePublishesAndTracksIt() = runTest(dispatcher) {
        enterLive()
        assertTrue(coordinator.canShare.value)

        val point = coordinator.addSharedPoint("Bilen", "parkert", LatLng(60.0, 10.0), "#FF5722")

        assertEquals("Bilen", point?.name)
        assertEquals(listOf("Bilen"), coordinator.mySharedPoints.value.map { it.name })
        assertEquals(listOf("Bilen"), createdSessions[0].sharedPoints.map { it.name })
    }

    @Test
    fun addSharedPointWhenNotSharingIsNoOp() = runTest(dispatcher) {
        assertEquals(false, coordinator.canShare.value)
        val point = coordinator.addSharedPoint("Bilen", "", LatLng(60.0, 10.0), "#FF5722")
        assertNull(point)
        assertTrue(coordinator.mySharedPoints.value.isEmpty())
    }

    @Test
    fun moveAndRemoveSharedPoint() = runTest(dispatcher) {
        enterLive()
        val point = requireNotNull(coordinator.addSharedPoint("Post", "", LatLng(60.0, 10.0), "#FF5722"))

        coordinator.moveSharedPoint(point.id, LatLng(61.0, 11.0))
        assertEquals(LatLng(61.0, 11.0), coordinator.mySharedPoints.value.first().latLng)
        assertEquals(61.0, createdSessions[0].sharedPoints.last().latLng.latitude)

        coordinator.removeSharedPoint(point.id)
        assertTrue(coordinator.mySharedPoints.value.isEmpty())
        assertEquals(listOf(point.id), createdSessions[0].deletedPointIds)
    }

    @Test
    fun sharedPointsAreClearedWhenSharingEnds() = runTest(dispatcher) {
        enterLive()
        coordinator.addSharedPoint("Post", "", LatLng(60.0, 10.0), "#FF5722")
        assertEquals(1, coordinator.mySharedPoints.value.size)

        liveShareUntilMillis.value = 0L // sharing ends

        assertEquals(OnlineTrackingCoordinator.Mode.NONE, coordinator.mode.value)
        assertTrue(coordinator.mySharedPoints.value.isEmpty())
        assertEquals(false, coordinator.canShare.value)
    }

    @Test
    fun sharedPointsAreRepublishedToTheNextSession() = runTest(dispatcher) {
        enterLive()
        coordinator.addSharedPoint("Post", "", LatLng(60.0, 10.0), "#FF5722")

        // Switch LIVE -> RECORDING: the old track stops (server drops its points), a fresh session
        // must re-publish the points the user still has.
        enterRecording()

        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)
        assertEquals(listOf("Post"), coordinator.mySharedPoints.value.map { it.name })
        assertEquals(listOf("Post"), createdSessions[1].sharedPoints.map { it.name })
    }

    @Test
    fun shareOnlyWithExistingTrackPointsSyncsRatherThanStarts() = runTest(dispatcher) {
        // RECORDING with a track that already has points (e.g. from continuing
        // a saved-but-resumed track) should sync, not start.
        onlineTrackingEnabled.value = true
        currentTrack.value = Track(
            name = "Resumed",
            points = listOf(TrackPoint(LatLng(60.0, 10.0), timestamp = now)),
            startTime = now,
            isRecording = true,
        )
        isRecording.value = true

        assertEquals(OnlineTrackingCoordinator.Mode.RECORDING, coordinator.mode.value)
        assertNull(createdSessions[0].startedTrackName)
        assertEquals("Resumed", createdSessions[0].syncedTrack?.name)
    }
}
