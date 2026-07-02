# Implementation Plan: Multi-Track View

Feature for **henrik242/where** (Kotlin Multiplatform + Compose Multiplatform hiking app).
Branch/worktree: `worktree-multi-track-view`.

## Goal

Show **multiple saved tracks on the map at the same time**, each in a distinct color, replacing
the current single-`viewingTrack` model.

### Decisions (confirmed with user)

1. **Selection = Both.** Keep the per-track "Show on Map" button but make it **additive** (adds to
   the visible set instead of replacing), *and* add a **multi-select bulk action** in the tracks
   list ("Show N on map").
2. **Focus = tap focuses one.** All selected tracks stay drawn; tapping a track line focuses it
   (name banner + altitude chart), and the non-focused tracks are **dimmed**. Matches today's
   tap-to-focus model, extended to a set.
3. **Navigation and recording stay single-track** and take over the view as they do today.
   Multi-track applies only to passive viewing.

---

## Current architecture (verified)

Package root `no.synth.where`. Shared UI in `shared/src/commonMain`; Android map plumbing in
`app/src/main/java`; iOS in `shared/src/iosMain` (+ native `iosApp/Where/MapViewFactory.swift`).

- **State**: `TrackRepository` (`shared/.../data/TrackRepository.kt`) holds
  `viewingTrack: StateFlow<Track?>` (L33), `trackFocused: StateFlow<Boolean>` (L36),
  `navigation: StateFlow<NavigationSession?>` (L39). Setters `setViewingTrack` (L203, resets
  focus), `clearViewingTrack` (L208), `setTrackFocused` (L213). `startNavigation` (L217) reuses
  `_viewingTrack = track` for route rendering + bounds; `stopNavigation` (L227) clears it.
- **ViewModel**: `MapScreenViewModel` (`app/.../ui/MapScreenViewModel.kt`) re-exposes
  `viewingTrack` (L32), `trackFocused`, `selectTrack`/`deselectTrack`/`clearViewingTrack`.
- **Model**: `Track` (`shared/.../data/Track.kt`) = `id, name, points: List<TrackPoint>, …`, with
  `bounds(): LatLngBounds?`, `getDistanceMeters()`, `hasElevationData()`. `LatLng.distanceTo`.
- **Android render**: `MapScreen.kt` L305 `LaunchedEffect(currentTrack, viewingTrack, navigation,
  mapInstance)` calls `MapRenderUtils.updateTrackOnMap(style, track, isCurrentTrack)`
  (`shared/src/androidMain/.../MapRenderUtils.kt` L54) — a **single** source `"track-source"` /
  layer `"track-layer"`, fixed color red `#FF0000` (recording) or blue `#0000FF` (viewing).
  The same call is repeated inside `MapLibreMapView.kt` on style (re)load at L155, L239, L437.
- **iOS render**: `IosMapScreen.kt` L354 `LaunchedEffect(viewingTrack, navigation)` calls
  `mapViewProvider.updateTrackLine(buildTrackGeoJson(track.points), "#0000FF")` (also at L810);
  interface `MapViewProvider.updateTrackLine(geoJson, color)` / `clearTrackLine()`
  (`shared/src/iosMain/.../MapViewProvider.kt` L32-33), implemented in Swift MapViewFactory.
- **Overlays**: `MapOverlays.kt` takes `viewingTrack: Track?`, `trackFocused: Boolean`
  (L576-577); `focusedTrack = if (trackFocused) viewingTrack else null` (L613) drives the banner
  (`ViewingTrackBanner` L212, L744) and `TrackAltitudeChart`. FAB column hidden while focused.
- **Hit-test**: `TrackUtils.findTappedTrack(tap, track, tolerance)` in `shared/.../data/`
  (point-to-segment in a local metric projection). Called on Android `MapLibreMapView.kt` L333 and
  iOS `IosMapScreen.kt` L445, with a zoom-scaled tolerance; a miss triggers unfocus.
- **GeoJSON**: `MapGeoJson.kt` `buildTrackGeoJson(points): String`.
- **Tracks list**: `TracksScreenContent` / `TrackItem` (`shared/.../ui/TracksScreenContent.kt`)
  with `onShowOnMap(track)` and `onNavigate(track)` callbacks. Wired in `WhereApp.kt` L136
  (`setViewingTrack`) and `IosApp.kt` L230.

---

## Design approach

**Data-driven line styling instead of per-track layers.** Rather than adding N MapLibre
layers/sources and reconciling them, render *all* visible tracks (recording + viewing set) as a
single `FeatureCollection` in one source / one `LineLayer`, where each feature carries its own
`color`, `width`, and `opacity` properties and the layer reads them via `["get", "color"]`
expressions. This:

- handles any number of tracks with one source/layer (no add/remove churn on the style),
- keeps the recording track (red) and the navigation split-line paths untouched conceptually,
- works identically on Android MapLibre and iOS MLN\* (both support data-driven `get` expressions),
- centralizes "what to draw" into one shared `buildTracksGeoJson(...)` so the three Android
  redraw sites and the two iOS sites all feed from the same computation.

**State**: replace the single `viewingTrack` slot with an ordered `viewingTracks: List<Track>`
(insertion order = color index, stable) plus a `focusedTrackId: String?` (replaces the
`trackFocused: Boolean`). Navigation keeps its own `NavigationSession` and takes over rendering as
today (multi-track layer suppressed while navigating).

**Colors**: a shared palette `TrackColors` (8-ish distinct, colorblind-friendly hues that read over
Kartverket topo tints). Color = `palette[index % size]`. Recording track stays red `#FF0000`.
When a track is focused, non-focused viewing tracks drop to ~0.35 opacity and the focused one gets
full opacity + slightly wider stroke.

---

## Task 1 — Shared color palette

**New file:** `shared/src/commonMain/kotlin/no/synth/where/ui/map/TrackColors.kt`

```kotlin
package no.synth.where.ui.map

object TrackColors {
    // Distinct hues readable over Kartverket yellow/green topo tints.
    val palette = listOf(
        "#1E88E5", "#43A047", "#8E24AA", "#FB8C00",
        "#00897B", "#E53935", "#3949AB", "#6D4C41",
    )
    const val RECORDING = "#FF0000"
    fun forIndex(i: Int) = palette[((i % palette.size) + palette.size) % palette.size]
}
```

Unit-test `forIndex` wraps and never throws.

---

## Task 2 — Multi-track state in `TrackRepository`

**Edit:** `shared/.../data/TrackRepository.kt`

Replace the single viewing slot + boolean focus with a list + focused id:

```kotlin
private val _viewingTracks = MutableStateFlow<List<Track>>(emptyList())
val viewingTracks: StateFlow<List<Track>> = _viewingTracks.asStateFlow()

private val _focusedTrackId = MutableStateFlow<String?>(null)
val focusedTrackId: StateFlow<String?> = _focusedTrackId.asStateFlow()

fun addViewingTrack(track: Track) {
    if (_viewingTracks.value.none { it.id == track.id }) {
        _viewingTracks.value = _viewingTracks.value + track
    }
    _focusedTrackId.value = track.id          // additive show focuses the newcomer
}

fun setViewingTracks(tracks: List<Track>) {   // bulk multi-select
    _viewingTracks.value = tracks
    _focusedTrackId.value = null
}

fun removeViewingTrack(id: String) {
    _viewingTracks.value = _viewingTracks.value.filterNot { it.id == id }
    if (_focusedTrackId.value == id) _focusedTrackId.value = null
    if (_viewingTracks.value.isEmpty()) _focusedTrackId.value = null
}

fun clearViewingTracks() {
    _viewingTracks.value = emptyList()
    _focusedTrackId.value = null
}

fun setFocusedTrack(id: String?) { _focusedTrackId.value = id }
fun toggleFocusedTrack(id: String) {
    _focusedTrackId.value = if (_focusedTrackId.value == id) null else id
}
```

**Navigation** stays single-track and takes over the view: change `startNavigation` to
`clearViewingTracks()` before setting `_navigation`, and keep rendering the split line from
`navigation.track` (below). `stopNavigation` just clears `_navigation` (viewing set already empty).
Remove the old `_viewingTrack` / `_trackFocused` fields and `setViewingTrack` /
`clearViewingTrack` / `setTrackFocused`.

Add a helper for camera fit:

```kotlin
// in Track.kt or a TrackUtils extension
fun combinedBounds(tracks: List<Track>): LatLngBounds?  // union of each track.bounds()
```

---

## Task 3 — Shared GeoJSON builder for the whole set

**Edit:** `shared/.../ui/map/MapGeoJson.kt`

```kotlin
data class RenderableTrack(
    val id: String,
    val points: List<TrackPoint>,
    val color: String,
    val width: Double,
    val opacity: Double,
)

/** Assigns palette colors by index and dims non-focused tracks. Recording track (if any) is red. */
fun renderableTracks(
    viewing: List<Track>,
    focusedId: String?,
    recording: Track?,
): List<RenderableTrack> { /* palette by index; focused full/opaque+wider, others dimmed */ }

/** One FeatureCollection; each LineString feature has color/width/opacity properties. */
fun buildTracksGeoJson(tracks: List<RenderableTrack>): String
```

Rules: skip tracks with < 2 points; if `focusedId == null` all viewing tracks render at full
opacity; else focused = opacity 1.0 / width 6, others = opacity 0.35 / width 4; recording is
always `TrackColors.RECORDING`, full opacity, drawn last (on top). Unit-test the color/opacity
assignment and the emitted JSON shape.

---

## Task 4 — Android renderer: data-driven line layer

**Edit:** `shared/src/androidMain/.../MapRenderUtils.kt`

Add a replacement for `updateTrackOnMap`:

```kotlin
fun updateTracksOnMap(style: Style, geoJson: String) {
    val sourceId = "track-source"; val layerId = "track-layer"
    val existing = style.getSourceAs<GeoJsonSource>(sourceId)
    if (existing != null) {
        existing.setGeoJson(geoJson)              // update in place, no layer churn
    } else {
        style.addSource(GeoJsonSource(sourceId, geoJson))
        style.addLayer(LineLayer(layerId, sourceId).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(Expression.get("width")),
            PropertyFactory.lineOpacity(Expression.get("opacity")),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ))
    }
}
```

Keep `updateTrackOnMap` deletable once all callers move over. An empty FeatureCollection clears the
line (set empty geojson).

---

## Task 5 — iOS renderer: data-driven line layer

**Edit:** `shared/src/iosMain/.../MapViewProvider.kt` — add:

```kotlin
fun updateTracks(geoJson: String)   // single source/layer, data-driven color/width/opacity
```

Keep `clearTrackLine()`; `updateTracks("")`/empty collection clears.

**Edit:** `iosApp/Where/MapViewFactory.swift` — implement `updateTracks`: one `MLNShapeSource`
(`track-source`) + one `MLNLineStyleLayer` (`track-layer`) whose `lineColor`, `lineWidth`,
`lineOpacity` are set from feature attributes via
`NSExpression(forKeyPath: "color")` etc. (data-driven), mirroring the Android constants. Note in
the plan header: **keep Android/iOS track styling constants in sync** (per CLAUDE.md layout note).

---

## Task 6 — Multi-track hit-test

**Edit:** `shared/.../data/TrackUtils.kt`

```kotlin
/** Nearest viewing track within tolerance, or null. */
fun findTappedTrack(tap: LatLng, tracks: List<Track>, maxDistanceMeters: Double): Track? =
    tracks.filter { it.points.size >= 2 }
        .minByOrNull { minDistanceToTrackMeters(tap, it) }
        ?.takeIf { minDistanceToTrackMeters(tap, it) <= maxDistanceMeters }
```

(Keep the existing single-track overload or delete once callers migrate.) Unit-test: overlapping
tracks pick the closest; tap outside all returns null.

---

## Task 7 — Rendering wiring (Android)

**Edit:** `app/.../ui/MapScreen.kt`

- Collect `viewingTracks` and `focusedTrackId` instead of `viewingTrack`/`trackFocused`.
- Replace the L305 `LaunchedEffect` body:

```kotlin
LaunchedEffect(currentTrack, viewingTracks, focusedTrackId, navigation, mapInstance) {
    val style = mapInstance?.style ?: return@LaunchedEffect
    val renderables = if (navigation != null)
        renderableTracks(emptyList(), null, currentTrack)   // nav split-line drawn separately
    else
        renderableTracks(viewingTracks, focusedTrackId, currentTrack)
    MapRenderUtils.updateTracksOnMap(style, buildTracksGeoJson(renderables))
    // fit camera to the union of viewing bounds when the SET changes (not on focus toggle)
}
```

  Split the camera-fit into its own `LaunchedEffect(viewingTracks, mapInstance)` keyed on the set
  size/ids so focusing doesn't refit; use `combinedBounds(viewingTracks)`.
- Update the three in-`MapLibreMapView.kt` redraw sites (L155/L239/L437) the same way: they should
  redraw from the same `renderableTracks(...)`/`buildTracksGeoJson(...)`. Pass `viewingTracks`,
  `focusedTrackId` (and keep `currentTrack`) into `MapLibreMapView` instead of the single
  `viewingTrack`. The click listener at L333 uses `TrackUtils.findTappedTrack(commonPoint,
  viewingTracksState.value, tolerance)`; a hit calls `onTrackClick(track.id)`; a miss calls
  `onMapClickOutsideTrack()` (unfocus) when the set is non-empty.
- Camera zoom guard L411: `viewingTrack == null` -> `viewingTracks.isEmpty()`.
- Back handler L139: `enabled = viewingTracks.isNotEmpty()`; first back unfocuses if focused, else
  clears the whole set (`clearViewingTracks`).

**Edit:** `app/.../ui/MapScreenViewModel.kt`
- Re-expose `viewingTracks`, `focusedTrackId`.
- Actions: `onTrackTapped(id) = repo.toggleFocusedTrack(id)`, `unfocusTrack() = repo.setFocusedTrack(null)`,
  `removeViewingTrack(id)`, `clearViewingTracks()`.

---

## Task 8 — Rendering wiring (iOS)

**Edit:** `shared/src/iosMain/.../IosMapScreen.kt`
- Collect `viewingTracks`, `focusedTrackId` from the repo.
- Replace the L354 and L810 render effects with a single effect keyed on
  `(viewingTracks, focusedTrackId, navigation)` that calls
  `mapViewProvider.updateTracks(buildTracksGeoJson(renderableTracks(...)))`, suppressing the set
  while navigating (nav split-line path unchanged). Fit camera to `combinedBounds(viewingTracks)`
  on set change.
- Hit-test at L445: `TrackUtils.findTappedTrack(tapLocation, viewingTracks, tolerance)` -> on hit
  `trackRepository.toggleFocusedTrack(hit.id)`; on miss with a non-empty set, `setFocusedTrack(null)`.
- Also update `IosLayerHexMapScreen.kt` if it renders tracks via the old single call.

---

## Task 9 — Overlays: focused banner + chart for the set

**Edit:** `shared/.../ui/map/MapOverlays.kt` and `MapScreenContent.kt`

- Replace params `viewingTrack: Track?` + `trackFocused: Boolean` with
  `viewingTracks: List<Track>` + `focusedTrackId: String?`.
- `focusedTrack = viewingTracks.firstOrNull { it.id == focusedTrackId }` drives the existing
  `ViewingTrackBanner` and `TrackAltitudeChart` (chart source unchanged, just the focused track).
- `ViewingTrackBanner`: add a small **color swatch** (the focused track's palette color) next to
  the name so users can correlate line ↔ banner. `onClose` now **removes just the focused track**
  from the set (`removeViewingTrack(focusedId)`); keep a separate affordance/back gesture to clear
  all. `hasTopOverlay` / FAB-hiding logic keyed on `focusedTrack != null` (unchanged shape).
- `MapScreenContent`: thread the two new params through with safe defaults; update its call sites in
  `MapScreen.kt` (L479/L616) and `IosMapScreen.kt` (L608).
- Update the `@Preview`s (`MapScreen.kt` L834/L876) to pass a small `viewingTracks` list +
  `focusedTrackId`.

> Note: user chose "tap focuses one" over a persistent list overlay, so there is **no** always-on
> track list on the map. Removal is via the focused banner's close (removes that track) and back
> (clears all). If discoverability proves weak we can add the list overlay later — out of scope.

---

## Task 10 — Tracks list UI (both entry points)

**Additive "Show on Map"** — repoint the existing callback:
- `WhereApp.kt` L136: `trackRepository.addViewingTrack(track)` (was `setViewingTrack`).
- `IosApp.kt` L230: same.
Each tap now adds the track and navigates to the map; the newcomer is auto-focused.

**Multi-select bulk action** — `shared/.../ui/TracksScreenContent.kt`:
- Add a selection mode: long-press a `TrackItem` enters selection mode; items show a leading
  checkbox; a top-app-bar / bottom-bar action **"Show N on map"** appears with a count.
- Confirm -> `onShowSelectedOnMap(selectedTracks)` -> `trackRepository.setViewingTracks(selected)`
  and navigate to the map. Add `onShowSelectedOnMap: (List<Track>) -> Unit` to
  `TracksScreenContent` and both platform `TracksScreen` wrappers.
- Keep selection state as `remember { mutableStateSetOf<String>() }` in the content composable;
  clear on exit. Follow Material 3 multi-select list patterns (contextual top bar with a count and
  a close/clear action).

---

## Task 11 — Strings & icons

- New strings in `shared/src/commonMain/composeResources/values/strings.xml` + `values-nb/`:
  `show_n_on_map` (`"Show %1$d on map"` / `"Vis %1$d på kartet"`), `select_tracks` /
  `remove_track` (reuse existing `delete`/`close_track_view` where possible).
- Color swatch uses a plain Compose `Box`/`Canvas`, no new drawable.

---

## Task 12 — Tests

`shared/src/commonTest` (run via `./gradlew :shared:testAndroidHostTest`):
- `TrackColors.forIndex` wrap-around.
- `renderableTracks`: palette assignment by index, dimming when focused, recording=red on top.
- `buildTracksGeoJson`: feature count, per-feature `color/width/opacity` props, <2-point skip.
- `findTappedTrack(list)`: closest-of-overlapping, miss returns null.
- `combinedBounds`: union of multiple tracks; empty list -> null.

App-module state tests (`MapScreenStateTest` / `MapScreenRegressionTest`) reference `viewingTrack`
by name — update to `viewingTracks`/`focusedTrackId`.

---

## Verification checklist

- [ ] "Show on Map" on several tracks in turn draws them all, each a distinct color; camera fits
      the union.
- [ ] Multi-select -> "Show N on map" draws exactly that set at once.
- [ ] Tapping a line focuses it (banner + swatch + altitude chart); other lines dim; tapping it
      again or tapping empty map unfocuses (lines return to full color).
- [ ] Focused banner close removes just that track; back clears all; FABs/zoom restore when empty.
- [ ] Recording track still red and on top; unaffected by the viewing set.
- [ ] Starting navigation hides the viewing set and shows only the grey/blue split line; stopping
      returns to normal (empty set).
- [ ] Style reload (Android layer switch) redraws the full set (all three redraw sites).
- [ ] Builds on Android and iOS; `MapViewFactory.swift` `updateTracks` matches Android styling;
      previews compile.

## Out of scope / follow-ups

- Persistent on-map track list overlay with per-row visibility toggles (user chose tap-to-focus).
- Multi-track during navigation (context lines while navigating).
- Per-track color customization / persistence of the viewing set across app restarts.
- Legend export or sharing multiple tracks as one file.
