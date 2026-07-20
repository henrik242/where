# Release history

Version numbers are the git commit count at build time; gaps between tag numbers are
builds that were never released. Each tag below marks a store release; dates are the
tag dates.

## Unreleased

- Tap the route while navigating to see its altitude chart, like a regular track
- Stopping navigation keeps the track on the map in detail view instead of removing it
- Import several tracks at once, or a whole zip, filing them straight into a folder
- Select all or none of a folder's tracks from its menu

## v488 (2026-07-16)

- Organize saved tracks into folders, shown as collapsible sections with unfiled tracks
  at the top
- Move tracks into folders one at a time or several at once, rename and remove folders,
  with an undo option

## v481 (2026-07-12)

- Map location puck uses fused location and recovers when location providers are toggled

## v480 (2026-07-12)

- Fixed a crash when the map location layer was read before it finished activating

## v478 (2026-07-11)

- Satellite base layer (EOX Sentinel-2 cloudless)
- Map rotation that follows your heading, via a three-state location button
- Queued offline map downloads (one at a time) with a dedicated Downloads page
- Configurable detail level for offline map downloads
- New earth-tone color palette replacing the purple/cobalt UI
- Persistent notification while navigating, with notification permission requested at startup
- Indicator in the saved tracks list for tracks shown on the map
- Fixed offline map caching regressions and added a low-storage warning when caching fails
- Fixed background battery drain from UI polling loops

## v465 (2026-07-07)

- Confirmation dialog before closing a viewed track
- Renamed "route" to "track" in the remaining UI strings

## v464 (2026-07-07)

- Navigate along a saved track: remaining distance, elevation, off-course arrow pointing
  back to the route, reverse-direction toggle, and confirmation before stopping
- Multi-track view with per-track colors and tap-to-focus track mode
- Track cropping with undo
- Elevation chart scrubbing marks the matching point on the map
- Two-finger distance measurement rendered as native MapLibre layers; the measured line
  can be handed off to the ruler
- Faster track import: parsing runs off the main thread, large GPX files no longer slow
  down quadratically, progress and a confirmation snackbar, real file names for files
  opened from other apps
- Tracks saved to the public Downloads folder on Android

## v437 (2026-05-27)

- Example coordinates shown in the settings format dropdown

## v436 (2026-05-26)

- Fixed iOS settings: coordinate format selection and URL import wiring

## v433 (2026-05-22)

- Fixed crash when opening single-point tracks
- Improved the live-tracking landing page and added a geocoding cache on the web server
- Security hardening: Android notification intents restricted to the app, web XSS and
  prototype-pollution fixes

## v423 (2026-05-08)

- Improved GPS responsiveness in low-signal terrain
- Internal: web server restructured into server/client/shared modules; fixed iOS builds
  from Android Studio

## v414 (2026-04-26)

- Time-limited live-location sharing
- "Direkte" (live) chip on the map replaces the inline live-tracking toggle
- Two-finger distance line stays anchored to the map for 15 seconds; fixed a line offset
  on iOS

## v406 (2026-04-13)

- Coordinate grid overlay with MGRS cell labels and UTM zone boundaries
- Coordinate format setting, including DMS
- Map label fonts bundled with the app instead of fetched from a CDN
- Removed the county borders overlay

## v398 (2026-04-10)

- Follow a friend's live track on your own map

## v397 (2026-04-03)

- Path-based live-tracking URLs instead of query parameters
- Mac Catalyst support for proper macOS input handling

## v395 (2026-04-02)

- Two-finger distance measurement on the map
- Viewer count shown while recording and on the live-tracking settings page
- Fixed live-tracking resource leaks and a race condition in the update queue

## v387 (2026-04-01)

- Distance to your position shown in the crosshair info card
- Offline chip moves aside when the compass needle is visible

## v384 (2026-03-29)

- Release-pipeline fixes and an optional submit-for-review step; no user-facing changes

## v381 (2026-03-27)

- First-time info dialog when enabling live tracking
- Fixed live tracking on iOS not starting when enabled while recording
- Automated store deployment for Android and iOS (internal)

## v340 (2026-03-23)

- GPX import from URLs and FIT file import from local files and URLs
- Support for all GPX point types, with an import confirmation dialog
- Fixed crash when tapping the location button before the map was ready
- Sponsor button in settings and on the web about page

## v325 (2026-03-19)

- Fixed iOS crosshair not updating on map pan

## v323 (2026-03-19)

First tagged release. Highlights of the initial development:

- Offline maps for Norway: Kartverket (topo, toporaster, nautical charts), MapAnt,
  OpenTopoMap, and OpenStreetMap, downloaded via a hexagonal grid picker
- GPS track recording that survives screen lock, with saved tracks and points of interest
- GPX import and export, plus track import from Garmin Connect, Komoot, and UT.no URLs
- Real-time location sharing with a web viewer (Bun server) and HMAC-signed updates
- Overlays: waymarked trails, NVE avalanche terrain (slope steepness and runout zones,
  offline-capable), hillshade
- Crosshair with live elevation and slope readout from offline terrain data
- Place search with history, reverse geocoding with landmark and peak detection
- Ruler for distance measurement
- Kotlin Multiplatform migration: shared data layer and Compose UI, iOS app with feature
  parity (offline maps, recording, sharing, points of interest)
- Dark mode, Norwegian translation, opt-out crash reporting, privacy policy
