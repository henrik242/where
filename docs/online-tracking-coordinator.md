# OnlineTrackingCoordinator extraction plan

Extract the duplicated state-machine in `LocationTrackingService.applyState()`
(Android) and `LaunchedEffect(desiredClientMode)` (iOS) into a single shared
class in `commonMain`. Subsumes the band-aid `UserPreferences.isLiveShareActive()`
and the per-platform `ClientMode` enums and `liveTrackName` literal.

## Target shape

`shared/src/commonMain/kotlin/no/synth/where/data/OnlineTrackingCoordinator.kt`

```kotlin
class OnlineTrackingCoordinator(
    private val userPreferences: UserPreferences,
    private val trackRepository: TrackRepository,
    private val clientIdManager: ClientIdManager,
    private val trackingHint: String,
    private val parentScope: CoroutineScope,
    private val clock: () -> Long = ::currentTimeMillis,
    private val clientFactory: (
        serverUrl: String,
        clientId: String,
        trackingHint: String,
        canSend: () -> Boolean,
        onViewerCountChanged: (Int) -> Unit,
    ) -> OnlineTrackingClient = ::OnlineTrackingClient,
) {
    enum class Mode { NONE, RECORDING, LIVE }

    val mode: StateFlow<Mode>                   // current, after transitions settle
    val shouldTrackLocation: StateFlow<Boolean> // recording || liveActive
    val isLiveSharing: StateFlow<Boolean>       // mode == LIVE
    val isLive: StateFlow<Boolean>              // mode != NONE — used by RecordingCard et al.

    fun start()           // begin collecting flows
    fun close()           // shutdown
    fun sendPoint(latLng: LatLng, altitude: Double?, accuracy: Float?)
}
```

## Internal algorithm — `collectLatest` over a desired-mode flow

Replaces the `combine(...).distinctUntilChanged().collect { applyState }` +
manual `Mutex` + `serviceScope.launch { build client }` pattern.
`collectLatest` cancels the previous applyMode coroutine on every new emission,
eliminating the rapid-flip leak class entirely (no manual mutex needed).

```kotlin
private fun observe() {
    parentScope.launch {
        combine(
            trackRepository.isRecording,
            userPreferences.alwaysShareUntilMillis,
            userPreferences.onlineTrackingEnabled,
            userPreferences.offlineModeEnabled,
        ) { rec, until, online, offline ->
            val live = until > clock()
            val onlineActive = online && !offline
            DesiredState(
                shouldTrackLocation = rec || live,
                desiredMode = when {
                    !onlineActive || (!rec && !live) -> Mode.NONE
                    rec -> Mode.RECORDING
                    else -> Mode.LIVE
                },
            )
        }.distinctUntilChanged().collectLatest { applyDesired(it) }
    }
}

private suspend fun applyDesired(desired: DesiredState) {
    _shouldTrackLocation.value = desired.shouldTrackLocation
    if (desired.desiredMode == _mode.value) return

    val old = currentClient
    if (old != null) {
        withContext(NonCancellable) { old.stopTrack(); old.close() }
        currentClient = null
    }
    _mode.value = Mode.NONE

    if (desired.desiredMode == Mode.NONE) return

    val cid = clientIdManager.getClientId()
    val client = clientFactory(
        userPreferences.trackingServerUrl.value, cid, trackingHint,
        { !userPreferences.offlineModeEnabled.value },
        { userPreferences.updateViewerCount(it) },
    )
    currentClient = client
    when (desired.desiredMode) {
        Mode.RECORDING -> {
            val track = trackRepository.currentTrack.value
            if (track != null && track.points.isNotEmpty()) client.syncExistingTrack(track)
            else client.startTrack(track?.name ?: "Track")
        }
        Mode.LIVE -> client.startTrack("Live ${formatDateTime(clock(), "yyyy-MM-dd HH:mm")}")
        Mode.NONE -> {}
    }
    _mode.value = desired.desiredMode
}

fun sendPoint(latLng: LatLng, altitude: Double?, accuracy: Float?) {
    if (_mode.value == Mode.NONE) return
    if (!trackRepository.isRecording.value
        && userPreferences.alwaysShareUntilMillis.value <= clock()) return
    currentClient?.sendPoint(latLng, altitude, accuracy)
}
```

## Migration steps (each step ships green)

### Step 1 — introduce the coordinator without removing anything

- Add `OnlineTrackingCoordinator.kt` in `shared/commonMain`.
- Wire into `WhereApplication.coordinator` (lazy) and `AppDependencies.coordinator`.
- Call `coordinator.start()` from app boot.
- Coordinator runs alongside the existing platform code, idle. Gate actual
  client work behind a `coordinator.enabled` flag set to `false` initially.

### Step 2 — drain Android first (lower risk)

- Remove `State`, `ClientMode`, `observeState()`, `applyState()`,
  `transitionClient()` from `LocationTrackingService`.
- Service collects `coordinator.shouldTrackLocation` and `coordinator.mode`
  for foreground/notification decisions only.
- `locationCallback.onLocationResult` calls `coordinator.sendPoint(...)`
  instead of `onlineTrackingClient?.sendPoint(...)`.
- While here: `notificationTickJob` access is confined to a single dispatcher
  (already is — collector path — but make it explicit).
- Flip `coordinator.enabled = true`.
- Delete the now-dead `OnlineTrackingClient` field on the service.

### Step 3 — drain iOS

- Remove `private enum class ClientMode` from `IosMapScreen.kt`, the
  file-level `desiredClientMode`, and the `LaunchedEffect(desiredClientMode)`.
- Replace with `LaunchedEffect(coordinator.shouldTrackLocation.collectAsState().value)`
  for permission + `startTracking()/stopTracking()`.
- `IosLocationTracker.locationManager:didUpdateLocations:` calls
  `coordinator.sendPoint(...)`.
- Remove `IosLocationTracker.onlineTrackingClient` field and the
  `userPreferences` constructor param (was a band-aid for the deadline guard
  — coordinator owns the guard now).
- Remove `UserPreferences.isLiveShareActive()` (was added for the same band-aid).

### Step 4 — clean up

- Move `liveTrackName()` → coordinator private helper (single source).
- Rename per the new vocabulary: `alwaysShareUntilMillis` → `liveShareUntilMillis`,
  `startAlwaysShare` → `startLiveShare`, etc. (DataStore keys can stay under
  the old name or migrate explicitly).
- Replace `RemainingTime` boolean predicates with sealed class.
- Extract `LiveSharingChip` to `ui/map/LiveSharingChip.kt`.
- Replace duplicated formatters with one shared
  `@Composable fun formatRemaining(remainingMillis: Long): String`.
- Replace `LocationTrackingService.formatRemainingShort` with the same helper
  (now localized via `Context.getString`).
- Add `coordinator.isLiveSharing` consumed by both `MapScreen` (Android)
  and `IosMapScreen` — kill the inline `isLiveSharing` recomputation.

### Step 5 — tests

`OnlineTrackingCoordinatorTest`: feed a fake clock + fake `UserPreferences` +
fake `TrackRepository` + a `FakeOnlineTrackingClient` via the `clientFactory`
injection. Assert state transitions for:

- idle
- share-only on/off
- recording on/off
- recording-during-share
- share-during-recording
- expiry
- online-toggled-off mid-share
- regenerate-id (paired with `userPreferences.stopAlwaysShare()` precondition)
- offline-mode flips during share

This is the first time any of the live-tracking logic becomes unit-testable.

## What this does and doesn't do

**Does**

- Kills the duplicated state machine.
- Eliminates the `transitionClient` rapid-flip leak (`collectLatest` cancels
  predecessor before the next applyDesired runs).
- Single owner for client lifecycle.
- Testable.
- Centralizes the deadline guard so `IosLocationTracker` and `IosMapScreen`
  shed their `UserPreferences` dependency for that purpose.
- Unblocks future features (e.g. show "X viewers" everywhere, route-recording
  metadata) without per-platform plumbing.

**Doesn't**

- Change UX.
- Change the iOS-Always-prompt timing (separate fix).
- Change the notification ticker (could later become
  `coordinator.notificationText: StateFlow<String>`).
- Touch the web map.

## Risks

- **Coordinator scope outliving the service.** Coordinator runs on app-scoped
  `parentScope`. The Android service comes and goes. Coordinator must continue
  to emit even when service is down — fine for state, but `OnlineTrackingClient`
  instances will live past service death (which is probably desirable; the
  service was the wrong owner anyway).
- **Process death rehydration.** Coordinator boot reads
  `userPreferences.alwaysShareUntilMillis`; if the value is stale-future,
  coordinator will try to start a LIVE client. The auto-resume privacy concern
  needs to be solved here, not in `UserPreferences`. Suggest: clear
  `ALWAYS_SHARE_UNTIL` on cold-start and require fresh user opt-in on iOS.
  Build that into the migration.
- **iOS background.** Coordinator's `parentScope` is `Dispatchers.Default` on
  iOS; if iOS suspends the process between location fixes, the coordinator's
  combine-flow won't react until the next fix wakes the runtime. Sending from
  the location delegate via `coordinator.sendPoint` is fine because the
  deadline check is synchronous.

## Estimate

~2 days of work + tests. Step 1 is half a day (additive). Steps 2–3 are the
meat (~1 day). Step 4 is cleanup (~half day). Tests are another half day
but pay for themselves immediately.
