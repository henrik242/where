# Implementation Plan: Crop Saved Tracks

Feature for **henrik242/where** (Kotlin Multiplatform + Compose Multiplatform hiking app).

## Goal

Let the user trim the start and end of a saved track with two draggable handles, previewing the
cut **visually** in two places at once:

1. On the **elevation chart** at the bottom of the focused track view — the trimmed-off head/tail
   are shaded out, the kept span is highlighted, and live stats (kept distance) update as you drag.
2. On the **map** — the kept span is drawn emphasized in the track's color; the two trimmed ends
   are dimmed grey (reusing the navigation "split line" look).

### Decisions (confirmed with user)

- **Save = overwrite in place.** The cropped track replaces the original; trimmed points are gone.
- **Entry point = a Crop button on the track entry in the tracks list.** Tapping it opens the map
  focused on that track directly in crop mode (reusing the "Show on map" navigation path). No crop
  action on the map banner.

### Interaction model

- In the tracks list, expand a track → its action buttons include **Crop** (next to Show on map /
  Navigate / etc.). Tap it → adds the track to the viewing set, starts crop, and returns to the map.
- On the map, crop mode is active:
  - The bottom panel shows a `TrackCropChart` (instead of the read-only `TrackAltitudeChart`) with
    two draggable handles and shaded cut regions.
  - A crop header replaces the focused-track banner: title `Crop {name}`, a **Cancel** (X) and a
    **Save** (check).
  - The map redraws the focused track as three parts: kept (color, wide) + two grey trimmed ends.
  - FABs/zoom stay hidden (already hidden while a track is focused).
- **Cancel** → leaves crop mode, restores the normal chart/banner and full-line rendering (the track
  remains shown on the map).
- **Save** → overwrites the track with the cropped points, exits crop mode. The track stays focused
  with its new (shorter) shape.

Crop is only offered for tracks with >= 2 points; the handles enforce keeping >= 2 points
(`end > start`). Works for tracks without elevation too (flat baseline chart).

---

## Repo context (verified)

- **State**: `TrackRepository` (`shared/.../data/TrackRepository.kt`) holds `viewingTracks:
  StateFlow<List<Track>>` and `focusedTrackId: StateFlow<String?>`. The focused track drives the
  banner + chart. Other view state (viewing/focus/navigation) all lives here as `MutableStateFlow`,
  shared by both platforms — crop state belongs here too.
- **Model**: `Track` (`shared/.../data/Track.kt`): `id`, `name`, `points: List<TrackPoint>`,
  `startTime`, `endTime`. `TrackPoint(latLng, timestamp, altitude?, accuracy?)`.
- **Persistence**: `TrackDao.insertTrackWithPoints` inserts points whose `id` is
  `autoGenerate = true`, so re-inserting a track with fewer points **orphans the old surplus
  points**. Overwrite needs an explicit delete-then-insert (new DAO method).
- **Chart**: `TrackAltitudeChart` (`shared/.../ui/map/TrackAltitudeChart.kt`) is a pure `Canvas`
  drawing `Track.elevationProfileOrNull()` (`ElevationProfile.kt`). No slider dependency exists.
- **Map rendering**: `renderableTracks(viewing, focusedId, recording)` in `MapGeoJson.kt` resolves
  per-track color/width/opacity; `buildTracksGeoJson` emits one FeatureCollection consumed by
  `MapRenderUtils.updateTracksOnMap` (Android) and `updateTrackLine`/`MapViewFactory.swift` (iOS).
  Call sites: `MapScreen.kt:300` and `IosMapScreen.kt:183`, both keyed in a `LaunchedEffect`.
- **Overlays**: `MapOverlays.kt` (`BoxScope.MapOverlays`) computes `focusedTrack` (L627) and renders
  `ViewingTrackBanner` (L775) and `TrackAltitudeChart` (L842), with `chartHeight` measured to lift
  the bottom-left cards. `MapScreenContent.kt` threads params through.
- **ViewModel (Android)**: `MapScreenViewModel` re-exposes repo flows; iOS uses local `remember`
  state in `IosMapScreen.kt` but reads repo flows directly.
- **Icons**: `ic_edit.xml` exists; no crop icon — add `ic_crop.xml`. `ic_close`, and a check icon
  need verifying (add `ic_check.xml` if absent).
- **Colors**: `TrackColors` — palette + `RECORDING`. Add a `TRIMMED` grey (`#9E9E9E`, matching the
  nav completed-line grey).

---

## Task 1 — Pure crop logic + cumulative distances (shared, unit-tested)

**Edit:** `shared/.../data/Track.kt` (or a new `TrackCrop.kt` in `data/`)

```kotlin
/** Cumulative distance in meters at each point index (cum[0] == 0.0). */
fun Track.cumulativeDistances(): List<Double> { ... }

/**
 * A new Track keeping only points[startIndex..endIndex] (inclusive), with start/end times
 * recomputed from the kept points. Same id/name (overwrite). Requires 0 <= start < end <= lastIndex.
 */
fun Track.cropped(startIndex: Int, endIndex: Int): Track {
    val kept = points.subList(startIndex, endIndex + 1).toList()
    return copy(points = kept, startTime = kept.first().timestamp, endTime = kept.last().timestamp)
}
```

**New test:** `shared/src/commonTest/.../data/TrackCropTest.kt` — full track (no-op), trim head,
trim tail, both ends; verify point count, first/last coords, recomputed times; index clamping.

---

## Task 2 — Crop state in `TrackRepository` (shared)

**Edit:** `shared/.../data/TrackRepository.kt`

```kotlin
data class TrackCropState(val trackId: String, val startIndex: Int, val endIndex: Int)

private val _cropState = MutableStateFlow<TrackCropState?>(null)
val cropState: StateFlow<TrackCropState?> = _cropState.asStateFlow()

fun startCrop(trackId: String) {
    val t = _viewingTracks.value.firstOrNull { it.id == trackId } ?: return
    if (t.points.size < 2) return
    _cropState.value = TrackCropState(trackId, 0, t.points.lastIndex)
}

fun updateCrop(startIndex: Int, endIndex: Int) {
    val c = _cropState.value ?: return
    val t = _viewingTracks.value.firstOrNull { it.id == c.trackId } ?: return
    val last = t.points.lastIndex
    val s = startIndex.coerceIn(0, last - 1)
    val e = endIndex.coerceIn(s + 1, last)
    _cropState.value = c.copy(startIndex = s, endIndex = e)
}

fun cancelCrop() { _cropState.value = null }

fun applyCrop() {
    val c = _cropState.value ?: return
    val t = _viewingTracks.value.firstOrNull { it.id == c.trackId } ?: return
    val cropped = t.cropped(c.startIndex, c.endIndex)
    // update the in-memory viewing copy immediately so the map/chart reflect the new shape
    _viewingTracks.value = _viewingTracks.value.map { if (it.id == cropped.id) cropped else it }
    _cropState.value = null
    scope.launch { overwriteTrack(cropped) }
}

private suspend fun overwriteTrack(track: Track) {
    // build entity + re-indexed point entities (same as persistTrack) then:
    trackDao.replaceTrackWithPoints(entity, pointEntities)
}
```

Reset crop state defensively in `removeViewingTrack`/`clearViewingTracks`/`startNavigation` if the
cropped track leaves the viewing set (e.g. `if (_cropState.value?.trackId == id) _cropState.value = null`).

---

## Task 3 — Overwrite persistence (shared DAO)

**Edit:** `shared/.../data/db/TrackDao.kt`

```kotlin
@Query("DELETE FROM track_points WHERE trackId = :trackId")
suspend fun deletePointsForTrack(trackId: String)

@Transaction
suspend fun replaceTrackWithPoints(track: TrackEntity, points: List<TrackPointEntity>) {
    insertTrack(track)                 // REPLACE keeps the row/id
    deletePointsForTrack(track.id)     // clear stale points (autoGenerate ids won't overwrite)
    insertTrackPoints(points)
}
```

No schema change (no new columns/tables) → no Room migration needed.

---

## Task 4 — Map preview rendering (shared)

**Edit:** `shared/.../ui/map/MapGeoJson.kt` and `TrackColors.kt`

Add `const val TRIMMED = "#9E9E9E"` to `TrackColors`.

Add a crop-aware overload so the focused track splits into three features when cropping:

```kotlin
fun renderableTracks(
    viewing: List<Track>, focusedId: String?, recording: Track?,
    crop: TrackCropState? = null,
): List<RenderableTrack>
```

When `crop != null && track.id == crop.trackId`, replace that track's single `RenderableTrack` with:
- **kept**: `points.subList(start, end+1)`, color `forIndex`, width 6, opacity 0.9, id `"$id-kept"`
- **head**: `points.subList(0, start+1)`, color `TRIMMED`, width 4, opacity 0.35, id `"$id-head"`
  (only if `start > 0`)
- **tail**: `points.subList(end, lastIndex+1)`, color `TRIMMED`, opacity 0.35, id `"$id-tail"`
  (only if `end < lastIndex`)

(Head/tail include the boundary point so the grey visually meets the colored kept span.)
Extend `renderableTracksTest`/`MultiTrackRenderTest` with a crop case.

---

## Task 5 — `TrackCropChart` composable (shared)

**New file:** `shared/.../ui/map/TrackCropChart.kt`

A `Canvas` panel (same surface/rounded-top look as `TrackAltitudeChart`) that:
- Computes `cumulativeDistances()` once (`remember(track.id, track.points)`); total distance = last.
- Draws the elevation profile if `hasElevationData()`, else a flat baseline, across full width
  (x = cumDist[i] / total).
- Draws a translucent scrim over the two trimmed regions `[0, xStart]` and `[xEnd, width]`.
- Draws two draggable handle bars at `xStart`/`xEnd` (rounded pill + grab dot, ~24dp touch target).
- `pointerInput` with `detectDragGestures` (plus a hit-test picking the nearer handle on down):
  pointer x → target distance `(x/width)*total` → nearest index via binary search on cumDist →
  `onCropChange(start, end)`. Enforces `end > start` (via the repo's `updateCrop` clamp).
- A stats row: kept distance (`keptDistance.formatDistance()`) and maybe "n of m points".

Signature:
```kotlin
@Composable
fun TrackCropChart(
    track: Track,
    startIndex: Int,
    endIndex: Int,
    onCropChange: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

Pure Compose in `commonMain` → identical on Android and iOS, no per-platform code.

---

## Task 6 — Crop header + chart swap in overlays (shared)

**Edit:** `shared/.../ui/map/MapOverlays.kt`

1. **New `TrackCropHeader`** composable: a `Card` like the banner with title `Crop {name}`, a
   Cancel `IconButton` (`ic_close`) and a Save `IconButton` (`ic_check`, tinted primary).
2. In `BoxScope.MapOverlays` add params:
   ```kotlin
   cropState: TrackCropState? = null,
   onCropChange: (Int, Int) -> Unit = { _, _ -> },
   onCancelCrop: () -> Unit = {},
   onApplyCrop: () -> Unit = {},
   ```
   - `val cropping = cropState != null && cropState.trackId == focusedTrackId`
   - Top band: when `cropping`, render `TrackCropHeader` instead of `ViewingTrackBanner`.
   - Bottom panel: when `cropping`, render `TrackCropChart(focusedTrack, cropState.startIndex,
     cropState.endIndex, onCropChange, ...)` (measured into `chartHeight`) instead of
     `TrackAltitudeChart`.
   - `ViewingTrackBanner` itself is unchanged (no new action).

**Edit:** `MapScreenContent.kt` — thread the four new params through to `MapOverlays` (safe defaults).

---

## Task 7 — Tracks-list Crop button + navigation (entry point)

**Edit:** `shared/.../ui/TracksScreenContent.kt`
- Add `onCrop: (Track) -> Unit = {}` to `TracksScreenContent`, and `onCrop: () -> Unit = {}` to
  `TrackItem`. Wire it through the `items { TrackItem(..., onCrop = { onCrop(track) }) }` call.
- In the expanded action set, add a **Crop** `OutlinedButton` (`ic_crop`, label `crop_track`),
  enabled only when `track.points.size >= 2`. Place it logically next to Navigate (its own row or
  paired with Rename — match the existing 2-per-row layout).

**Edit:** `app/.../ui/TracksScreen.kt` — add `onCropTrack: (Track) -> Unit` param and pass
`onCrop = onCropTrack` into `TracksScreenContent`.

**Edit:** `app/.../WhereApp.kt` — wire the nav callback like `onShowTrackOnMap`:
```kotlin
onCropTrack = { track ->
    trackRepository.addViewingTrack(track)   // adds + focuses
    trackRepository.startCrop(track.id)
    navController.popBackStack<MapRoute>(false)
},
```

**Edit:** `shared/src/iosMain/.../IosApp.kt` — same wiring at the `TracksScreen`/`addViewingTrack`
call site (~L237): add `addViewingTrack(track)` + `startCrop(track.id)` + navigate back to the map.

## Task 8 — Android map wiring

**Edit:** `app/.../ui/MapScreenViewModel.kt`
```kotlin
val cropState = trackRepository.cropState
fun updateCrop(s: Int, e: Int) = trackRepository.updateCrop(s, e)
fun cancelCrop() = trackRepository.cancelCrop()
fun applyCrop() = trackRepository.applyCrop()
```

**Edit:** `app/.../ui/MapScreen.kt`
- Collect `val cropState by viewModel.cropState.collectAsState()`.
- Pass `cropState`, `onCropChange`, `onCancelCrop`, `onApplyCrop` into `MapScreenContent`.
- At the `renderableTracks(...)` call (L300): pass `crop = cropState`, and **add `cropState` to the
  `LaunchedEffect` keys** so the map redraws live while dragging.
- Update the `@Preview` if it constructs `MapScreenContent` with the changed param set.

**Edit:** `shared/src/androidMain/.../MapRenderUtils.kt` — no change (data-driven layer already
handles extra features).

---

## Task 9 — iOS map wiring

**Edit:** `shared/src/iosMain/.../ui/map/IosMapScreen.kt`
- `val cropState by trackRepository.cropState.collectAsState()`.
- Pass the four params into `MapScreenContent`, wired to
  `trackRepository.updateCrop/cancelCrop/applyCrop`.
- At the `renderableTracks(...)` call (L183): pass `crop = cropState` and add `cropState` to the
  `LaunchedEffect` keys.

The crop chart and header are shared composables, so iOS needs no new map UI code.
`MapViewFactory.swift` needs no change (extra grey features flow through the existing track layer).

---

## Task 10 — Icons & strings

- **Icons**: add `ic_crop.xml` (Material "crop") and, if missing, `ic_check.xml` to
  `shared/src/commonMain/composeResources/drawable/` (used by the list button + crop header).
- **Strings** (`values/strings.xml` + `values-nb/strings.xml`):
  - `crop_track` = "Crop" / "Beskjær" (list button + header title base)
  - `crop_title` = "Crop %1$s" / "Beskjær %1$s" (header)
  - `crop_save` = content desc "Save crop" / "Lagre beskjæring"
  - `crop_cancel` = content desc "Cancel crop" / "Avbryt beskjæring"
  - `crop_kept_distance` (optional) for the stats row.

---

## Verification checklist

- [ ] Expand a track in the list → a Crop button appears; tapping it opens the map in crop mode
      (crop header + crop chart), focused on that track.
- [ ] Dragging the start/end handles updates the shaded cut regions **and** the grey/colored split
      on the map, live, in sync.
- [ ] Handles can't cross; at least 2 points always kept.
- [ ] Save overwrites the track: the tracks list shows the same name, shorter distance; reopening
      shows only the kept span; no orphaned points linger (DB point count matches).
- [ ] Cancel restores the full line and read-only chart, no DB change.
- [ ] A track without elevation crops via a flat baseline chart; no crash.
- [ ] Recording and navigation are unaffected (crop only applies to a focused viewing track).
- [ ] Builds on Android and iOS; `:shared:testAndroidHostTest` green (crop + render tests).

## Out of scope / follow-ups

- Undo after save (overwrite is destructive by decision).
- Cropping the middle out / multi-range crop.
- A draggable crosshair syncing chart position ↔ map marker.
