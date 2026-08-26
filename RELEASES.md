# Release history

Version numbers are the git commit count at build time; gaps between tag numbers are
builds that were never released. Each tag below marks a store release; dates are the
tag dates.

## Unreleased

- Follow several friends at once: add up to five client IDs and each live track draws in its own
  colour, labelled with the client ID, with a banner listing who you follow
- Fixed a followed track losing its points when it went stale and resumed
- Fixed followed tracks on iOS drawing without their colour and client-ID label

## v544 (2026-08-24)

- Saved points take a description when you create them, not just when editing one afterwards
- Fixed a saved point's description, name and colour reverting when edited from the map: tapping
  the point again handed out its pre-edit values, and saving that dialog wrote them back
- The point colour picker shows which colour is selected, wraps so every colour stays reachable on
  narrow screens, and reads out its colour names
- Save is disabled until a point has a name, instead of closing the dialog and dropping what you
  typed, and a slow place-name lookup no longer overwrites a name you typed yourself
- Delete moved away from Save in the point dialog, and long descriptions scroll instead of pushing
  the buttons out of reach
- Fixed the navigated route not being tappable on iOS after starting or stopping navigation
-  Fixed a setting snapping back to its previous value when toggled twice in quick succession

## v534 (2026-08-20)

- Fixed a crash that could close the app when opening a screen with a text field, such as
  the map search or the rename dialogs

## v532 (2026-08-19)

- Saved points can be imported from GPX files: the Saved Points screen got an import
  button that reads the waypoints out of one or more files, or a whole zip, then lets you
  tick off which of them to keep
- Fixed ut.no import, which broke when the API it used was taken down. It now downloads
  the same GPX file the ut.no page links to, and also accepts that GPX link directly
- Place names resolve more often: lookups fall back to other OpenStreetMap servers when the
  main one is busy, and a point on a building now gets the building name instead of the
  nearest road

## v525 (2026-08-10)
- 
- New "Paths (OSM)" overlay with paths and tractor roads from OpenStreetMap, drawn like
  MapAnt's own path overlay, on top of any base map. Paved ways are solid and the rest dashed.
  Zoomed out it shows the main paths and fills in the rest as you zoom in, and it downloads
  for offline use like the other overlays
- New "Steepness (NVE)" overlay with slope steepness only; the existing NVE overlay is
  renamed "Steepness + Runout (NVE)" and the two replace each other when toggled
- Both NVE overlays now scale up past zoom 16 instead of going blank, since NVE's tile
  cache ends there
- Layer menu stays open when toggling an overlay, so you can flip several at once
- Removed the hillshade overlay and its downloadable terrain layer. Elevation and slope
  readouts are unaffected; they use a different tile set

## v516 (2026-08-09)

- Connect your Strava account (using your own Strava API app) to browse and import your
  planned routes into the tracks list
- Give each saved track its own colour, from a palette, a custom colour, or random;
  change it from the track list or from the map details view

## v511 (2026-08-02)

- Tapping the compass points the map north; tapping again locks it there and blocks
  rotation, and one more tap releases the lock

## v507 (2026-07-24)

- Map compass slides below the top banners (navigation, track details, saved point,
  friend tracking) instead of hiding behind them

## v504 (2026-07-20)

- Recording card shows live time, distance, ascent, and average speed
- Tap the route while navigating to see its altitude chart, like a regular track
- Stopping navigation keeps the track on the map in detail view instead of removing it
- Other viewed tracks stay visible (dimmed) while navigating
- Track detail actions moved into an overflow menu, including start navigation
- Traversed part of the navigated route is drawn as a dotted line in the route colour
- U-turns while navigating recompute progress instead of resetting it, with a clearer
  U-turn arrow
- OpenTopoMap capped at zoom 17

## v492 (2026-07-17)

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
