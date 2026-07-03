# Implementation Plan: Elevation-chart Scrubbing Marker

Feature for **henrik242/where** (Kotlin Multiplatform + Compose Multiplatform hiking app).

## Goal

When a saved track is focused on the map and its altitude chart (`TrackAltitudeChart`) is shown,
touching or dragging on the chart shows a marker on the map at the corresponding point along the
track. Tapping places the marker; sliding scrubs it live.

## Interaction model

- Press on the chart → a marker appears on the map at the matching track point; a vertical indicator
  line + dot appear on the chart at the touch x.
- Drag → marker and indicator follow the finger live.
- Release → marker stays at the last position (so a plain tap is meaningful).
- The marker clears when the track view changes: unfocus/close, start crop, start navigation, or
  focus a different track.
- While a marker is active, the chart's label row shows the marked point's distance + elevation.

## Repo context (verified)

- `TrackAltitudeChart` (`shared/.../ui/map/TrackAltitudeChart.kt`) draws `Track.elevationProfileOrNull()`
  in a `Canvas` (88dp). It has the `Track`, so it can map a touch x → distance → `LatLng`.
- `Track.cumulativeDistances()` (`shared/.../data/TrackCrop.kt`, added by the crop feature) gives
  per-point cumulative meters; `TrackCropChart` already has a private `nearestIndex(cum, dist)`
  binary search — extract it to shared for reuse.
- Transient view state flows `TrackRepository` `StateFlow` → collected in `MapScreen`/`IosMapScreen`
  → rendered per platform (exactly how `cropState` works).
- Marker rendering idiom (remove-then-add source+layer) is already used for saved points and the
  continuously-dragged two-finger measurement, so it is proven for live drag.
  - Android: `MapRenderUtils` add/remove `GeoJsonSource` + `CircleLayer`.
  - iOS: `MapViewProvider` interface method + `MapViewFactory.swift` apply/remove with `MLNShapeSource`
    + `MLNCircleStyleLayer`, using the pending-geojson guard.
- GeoJSON point builders live in `MapGeoJson.kt` (`buildSavedPointsGeoJson`, etc.).

## Task 1 — Shared: distance→index helper + marker geojson

**Edit** `shared/.../data/TrackCrop.kt`: extract the nearest-index binary search as public shared:
```kotlin
/** Index of the point whose cumulative distance is closest to [meters]; [cum] must be ascending. */
fun nearestPointIndex(cum: List<Double>, meters: Double): Int { /* moved from TrackCropChart */ }
```
Update `TrackCropChart` to call it (drop its private copy).

**Edit** `shared/.../ui/map/MapGeoJson.kt`:
```kotlin
/** Marker point for the focused track's scrub [markerIndex], carrying the track's palette color as a
 *  `color` property; empty collection when nothing is marked. */
fun buildTrackMarkerGeoJson(viewing: List<Track>, focusedId: String?, markerIndex: Int?): String
```
(Empty FeatureCollection when null so the render path can stay unconditional.)

## Task 2 — Shared: marker state in `TrackRepository`

The marker is carried as a point **index** (not a LatLng) so the chart never has to reverse-lookup a
coordinate — which would desync on tracks that revisit a point (loops, out-and-back, stationary GPS).

```kotlin
private val _elevationMarker = MutableStateFlow<Int?>(null)
val elevationMarker: StateFlow<Int?> = _elevationMarker.asStateFlow()
fun setElevationMarker(index: Int?) { _elevationMarker.value = index }
```
Clear it (`_elevationMarker.value = null`) in `setFocusedTrack`, `toggleFocusedTrack`,
`setViewingTracks`, `removeViewingTrack`, `clearViewingTracks`, `startNavigation`, and `startCrop`.

## Task 3 — Shared: chart touch handling

**Edit** `TrackAltitudeChart`:
- Add params `onScrub: (Int?) -> Unit = {}`, `markerIndex: Int? = null` (drives the on-chart
  indicator, kept in sync with the map), and `markerColorHex: String? = null` (the focused track's
  color, so the chart indicator matches the map marker).
- `remember` `cum = track.cumulativeDistances()`.
- `pointerInput(track.id) { awaitEachGesture { ... } }`: on first down and on each move, compute
  `dist = (x/width).coerceIn(0,1) * totalDistance` and call `onScrub(nearestPointIndex(cum, dist))`.
  Handles tap (down) and drag (moves); keeps last on release. Consume moves so the map doesn't pan.
- Draw the indicator at `markerIndex` (the profile is downsampled, so map the point index to the
  nearest drawn sample); clamp the dot's y so it isn't clipped on a flat profile.
- While `markerLatLng != null`, show the marked point's distance + elevation in the label row.

## Task 4 — Shared: thread callback through overlays

**Edit** `MapOverlays.kt`: add `onElevationScrub: (Int?) -> Unit = {}` and `elevationMarker: Int? =
null`; pass to `TrackAltitudeChart(onScrub = onElevationScrub, markerIndex = elevationMarker,
markerColorHex = focusedTrackColor)`.
**Edit** `MapScreenContent.kt`: thread the two params through to `MapOverlays` (safe defaults).

## Task 5 — Android rendering + wiring

**Edit** `shared/src/androidMain/.../MapRenderUtils.kt`:
```kotlin
fun updateElevationMarkerOnMap(style: Style, geoJson: String) {
    // remove elevation-marker-layer + elevation-marker-source, then add source(geoJson) + CircleLayer
    // white fill, data-driven stroke = the track's `color` property, radius ~7, above the track layer
}
```
**Edit** `app/.../ui/MapScreenViewModel.kt`: expose `elevationMarker = trackRepository.elevationMarker`
and `setElevationMarker(latLng)`.
**Edit** `app/.../ui/MapScreen.kt`: collect `elevationMarker`; build `buildTrackMarkerGeoJson`;
`LaunchedEffect(markerGeoJson, mapInstance) { updateElevationMarkerOnMap(style, markerGeoJson) }`;
pass `onElevationScrub = { viewModel.setElevationMarker(it) }` and `elevationMarker` into
`MapScreenContent`.

## Task 6 — iOS rendering + wiring

**Edit** `shared/src/iosMain/.../ui/map/MapViewProvider.kt`: add `fun updateElevationMarker(geoJson: String)`.
**Edit** `iosApp/Where/MapViewFactory.swift`: implement with pending-geojson guard +
`applyElevationMarker`/`removeElevationMarker` (mirror saved points), `elevation-marker-source/-layer`.
**Edit** `shared/src/iosMain/.../ui/map/IosMapScreen.kt`: collect `elevationMarker`; build geojson;
`LaunchedEffect(markerGeoJson) { mapViewProvider.updateElevationMarker(markerGeoJson) }`; pass the two
params into `MapScreenContent`.

## Task 7 — Tests

- `TrackCropTest` (or a new `TrackMarkerTest`): `nearestPointIndex` — exact vertex, midpoint bias,
  clamping at ends, duplicate points.
- `MapGeoJson` test: `buildTrackMarkerGeoJson` with no marker is an empty FeatureCollection; a valid index emits one
  Point with correct lng,lat order.

## Verification checklist

- [ ] Focus a track with elevation → altitude chart shows. Tapping the chart drops a marker on the
      map at the matching point; the chart shows an indicator + distance/elevation readout.
- [ ] Dragging scrubs the marker along the track live on both chart and map.
- [ ] Releasing keeps the marker; leaving the track view / crop / navigation clears it.
- [ ] A track without elevation shows no chart (unchanged) — no scrubbing, no crash.
- [ ] Builds on Android + iOS; shared host tests green.

## Out of scope

- Snapping the map camera to the marker.
- A permanent distance axis / gridlines on the chart.
