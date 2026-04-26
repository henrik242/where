package no.synth.where.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import no.synth.where.data.geo.LatLng
import no.synth.where.util.currentTimeMillis
import no.synth.where.util.formatDateTime

/**
 * Single owner of the [TrackingSession] across both platforms.
 *
 * Combines `isRecording`, `liveShareUntilMillis`, `onlineTrackingEnabled` and
 * `offlineModeEnabled` into a desired [Mode] and drives session transitions
 * through `collectLatest` — when the desired state changes, any in-flight
 * `applyDesired` is cancelled before the next runs, so rapid state flips
 * cannot leak partially-built sessions.
 *
 * Platforms keep their own location-source plumbing (Android foreground
 * service / iOS `CLLocationManager`); they observe [shouldTrackLocation] to
 * know when to start/stop, and forward fixes through [sendPoint].
 *
 * Inputs are passed as granular flows / lambdas rather than `UserPreferences`
 * directly so the class can be unit-tested without a DataStore.
 *
 * @param parentScope is *not owned*: [close] cancels the observer job, not the
 * scope itself.
 */
class OnlineTrackingCoordinator(
    private val sources: Sources,
    private val getClientId: suspend () -> String,
    private val trackingHint: String,
    private val parentScope: CoroutineScope,
    private val clock: () -> Long = ::currentTimeMillis,
    private val sessionFactory: (SessionConfig) -> TrackingSession = { c ->
        OnlineTrackingClient(
            serverUrl = c.serverUrl,
            clientId = c.clientId,
            trackingHint = c.trackingHint,
            canSend = c.canSend,
            onViewerCountChanged = c.onViewerCountChanged,
        )
    },
) {
    /**
     * Reactive inputs the coordinator consumes. Granular so tests can drive
     * each independently with plain `MutableStateFlow`s.
     */
    data class Sources(
        val isRecording: StateFlow<Boolean>,
        val liveShareUntilMillis: StateFlow<Long>,
        val onlineTrackingEnabled: StateFlow<Boolean>,
        val offlineModeEnabled: StateFlow<Boolean>,
        val trackingServerUrl: StateFlow<String>,
        val currentTrack: StateFlow<Track?>,
        val onViewerCountChanged: (Int) -> Unit,
    )

    /** Inputs handed to the session factory at construction time. */
    data class SessionConfig(
        val serverUrl: String,
        val clientId: String,
        val trackingHint: String,
        val canSend: () -> Boolean,
        val onViewerCountChanged: (Int) -> Unit,
    )

    enum class Mode { NONE, RECORDING, LIVE }

    private val _mode = MutableStateFlow(Mode.NONE)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    private val _shouldTrackLocation = MutableStateFlow(false)
    val shouldTrackLocation: StateFlow<Boolean> = _shouldTrackLocation.asStateFlow()

    private val _isLiveSharing = MutableStateFlow(false)
    val isLiveSharing: StateFlow<Boolean> = _isLiveSharing.asStateFlow()

    @Volatile private var currentClient: TrackingSession? = null
    @Volatile private var observerJob: Job? = null

    private data class Desired(
        val shouldTrackLocation: Boolean,
        val mode: Mode,
    )

    fun start() {
        if (observerJob?.isActive == true) return
        observerJob = parentScope.launch {
            combine(
                sources.isRecording,
                sources.liveShareUntilMillis,
                sources.onlineTrackingEnabled,
                sources.offlineModeEnabled,
            ) { rec, until, online, offline ->
                val live = until > clock()
                val onlineActive = online && !offline
                Desired(
                    shouldTrackLocation = rec || live,
                    mode = when {
                        !onlineActive || (!rec && !live) -> Mode.NONE
                        rec -> Mode.RECORDING
                        else -> Mode.LIVE
                    },
                )
            }.distinctUntilChanged().collectLatest { applyDesired(it) }
        }
    }

    fun close() {
        observerJob?.cancel()
        observerJob = null
        currentClient?.let { c ->
            c.stopTrack()
            c.close()
        }
        currentClient = null
        _mode.value = Mode.NONE
        _shouldTrackLocation.value = false
        _isLiveSharing.value = false
    }

    private suspend fun applyDesired(desired: Desired) {
        _shouldTrackLocation.value = desired.shouldTrackLocation
        if (desired.mode == _mode.value) return

        tearDown()
        if (desired.mode == Mode.NONE) return

        // Suspending part — cancellation here is fine, we have nothing to clean up.
        val cid = getClientId()
        buildAndStart(desired.mode, cid)
    }

    /**
     * Tears down the current client and resets state to [Mode.NONE]. All field
     * and StateFlow mutations happen inside `NonCancellable` so a `collectLatest`
     * cancellation mid-teardown cannot leave a stale `currentClient` reference.
     */
    private suspend fun tearDown() {
        withContext(NonCancellable) {
            val old = currentClient
            if (old != null) {
                old.stopTrack()
                old.close()
                currentClient = null
            }
            _mode.value = Mode.NONE
            _isLiveSharing.value = false
        }
    }

    /**
     * Builds a fresh client and registers it atomically (either fully set up
     * and stored in [currentClient], or never created at all).
     */
    private suspend fun buildAndStart(mode: Mode, cid: String) {
        withContext(NonCancellable) {
            val session = sessionFactory(
                SessionConfig(
                    serverUrl = sources.trackingServerUrl.value,
                    clientId = cid,
                    trackingHint = trackingHint,
                    canSend = { !sources.offlineModeEnabled.value },
                    onViewerCountChanged = sources.onViewerCountChanged,
                )
            )
            currentClient = session
            when (mode) {
                Mode.RECORDING -> {
                    val track = sources.currentTrack.value
                    if (track != null && track.points.isNotEmpty()) {
                        session.syncExistingTrack(track)
                    } else {
                        session.startTrack(track?.name ?: "Track")
                    }
                }
                Mode.LIVE -> {
                    session.startTrack("Live ${formatDateTime(clock(), "yyyy-MM-dd HH:mm")}")
                }
                Mode.NONE -> {}
            }
            _mode.value = mode
            _isLiveSharing.value = mode == Mode.LIVE
        }
    }

    /**
     * Forwards a fix to the active [TrackingSession]. Synchronously checks
     * mode + the deadline against `clock()` so a tail of fixes arriving after
     * expiry but before the state machine has reacted is never sent.
     */
    fun sendPoint(latLng: LatLng, altitude: Double?, accuracy: Float?) {
        if (_mode.value == Mode.NONE) return
        if (!sources.isRecording.value && sources.liveShareUntilMillis.value <= clock()) return
        currentClient?.sendPoint(latLng, altitude, accuracy)
    }
}
