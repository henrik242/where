# Release history

Version numbers are the git commit count at build time; gaps between tag numbers are
builds that were never released. Each tag below marks a store release; dates are the
tag dates.

## Unreleased

[diff](https://github.com/henrik242/where/compare/v579...main)

n/a

## v579 (2026-09-15)

[diff](https://github.com/henrik242/where/compare/v573...v579)

- Fixed the save-point name field scrambling fast keystrokes (e.g. "Jobb" becoming "Jbbo")

## v573 (2026-09-09)

[diff](https://github.com/henrik242/where/compare/v560...v573)

- Drop named points on the map while live-sharing; followers see them and can keep a
  copy, and they clear when the sharing session ends
- Give each followed friend a local nickname, shown instead of the client id
- A single GPS fix that teleports away and back no longer corrupts the track or your
  followers' view
- Closing the follow banner with two or more friends asks first, instead of wiping the
  whole follow list
- The map follows your course over ground while moving, not the compass

## v560 (2026-08-31)

[diff](https://github.com/henrik242/where/compare/v558...v560)

- Fixed a followed friend's name never drawing, which on iOS also hid the marker and halo
- One marker per friend instead of one per track; stopped friends are dimmed, the halo
  gets a white ring, names hide when zoomed out, and tapping a friend in the banner zooms
  to them
- Zoom buttons move below the follow banner instead of hiding for the whole trip

## v558 (2026-08-29)

[diff](https://github.com/henrik242/where/compare/v552...v558)

- Followed friends' markers grow with a halo when zoomed out, so they read over the topo
  colours
- Fixed live sharing going silent while the countdown kept running: iOS leaving the map
  screen, Android after a force-stop or reboot, and a share started from the permission
  prompt
- Sharing while offline, or on iOS with "While Using the App", now warns with a one-tap
  fix instead of counting down a share that reaches nobody
- Fixed backslashes showing around the quoted setting name under "How it works"

## v552 (2026-08-27)

[diff](https://github.com/henrik242/where/compare/v545...v552)

- Follow up to five friends at once, each track in its own colour and labelled with its
  client ID, with a banner listing who you follow
- Fixed a followed track losing its points when it went stale and resumed
- Fixed followed tracks on iOS drawing without their colour and label
- Strava route picker marks starred routes with a star icon instead of the word "Starred"

## v545 (2026-08-24)

[diff](https://github.com/henrik242/where/compare/v544...v545)

- Internal: iOS App Review contact info set on submit; no user-facing changes

## v544 (2026-08-24)

[diff](https://github.com/henrik242/where/compare/v534...v544)

- Saved points take a description when created, not only when edited afterwards
- Fixed a point's description, name and colour reverting when edited from the map
- Point colour picker shows the selection, wraps on narrow screens, and reads out colour
  names
- Save is disabled until a point has a name, and a slow place-name lookup no longer
  overwrites a name you typed
- Delete moved away from Save, and long descriptions scroll instead of pushing the buttons
  off-screen
- Fixed the navigated route not being tappable on iOS after starting or stopping navigation
- Fixed a setting snapping back when toggled twice quickly

## v534 (2026-08-20)

[diff](https://github.com/henrik242/where/compare/v532...v534)

- Fixed a crash when opening a screen with a text field, such as map search or a rename
  dialog

## v532 (2026-08-18)

[diff](https://github.com/henrik242/where/compare/v527...v532)

- Import saved points from GPX files or a zip, then tick which waypoints to keep
- Fixed ut.no import after its API was taken down; it now downloads the linked GPX file
  and accepts a GPX link directly
- Place names resolve more often, falling back to other OpenStreetMap servers, and a point
  on a building gets the building name instead of the nearest road

## v527 (2026-08-10)

[diff](https://github.com/henrik242/where/compare/v525...v527)

- OSM paths overlay draws paved ways solid and the rest dashed

## v525 (2026-08-10)

[diff](https://github.com/henrik242/where/compare/v519...v525)

- New "Paths (OSM)" overlay with paths and tractor roads from OpenStreetMap, over any base
  map; fills in detail as you zoom in and downloads for offline use
- New "Steepness (NVE)" overlay with slope only; the existing NVE overlay becomes
  "Steepness + Runout (NVE)" and the two replace each other when toggled
- Both NVE overlays scale past zoom 16 instead of going blank
- Layer menu stays open when toggling an overlay, so you can flip several at once
- Removed the hillshade overlay and its terrain layer; elevation and slope readouts are
  unaffected

## v519 (2026-08-09)

[diff](https://github.com/henrik242/where/compare/v516...v519)

- Tightened saved-track row spacing

## v516 (2026-08-09)

[diff](https://github.com/henrik242/where/compare/v511...v516)

- Connect your Strava account (using your own Strava API app) to browse and import your
  planned routes into the tracks list
- Give each saved track its own colour, from a palette, a custom colour, or random;
  change it from the track list or from the map details view

## v511 (2026-08-02)

[diff](https://github.com/henrik242/where/compare/v507...v511)

- Tapping the compass points the map north; tapping again locks it there and blocks
  rotation, and one more tap releases the lock

## v507 (2026-07-24)

[diff](https://github.com/henrik242/where/compare/v504...v507)

- Map compass slides below the top banners (navigation, track details, saved point,
  friend tracking) instead of hiding behind them

## v504 (2026-07-20)

[diff](https://github.com/henrik242/where/compare/v492...v504)

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

[diff](https://github.com/henrik242/where/compare/v488...v492)

- Import several tracks at once, or a whole zip, filing them straight into a folder
- Select all or none of a folder's tracks from its menu

## v488 (2026-07-16)

[diff](https://github.com/henrik242/where/compare/v481...v488)

- Organize saved tracks into folders, shown as collapsible sections with unfiled tracks
  at the top
- Move tracks into folders one at a time or several at once, rename and remove folders,
  with an undo option

## v481 (2026-07-12)

[diff](https://github.com/henrik242/where/compare/v480...v481)

- Map location puck uses fused location and recovers when location providers are toggled

## v480 (2026-07-12)

[diff](https://github.com/henrik242/where/compare/v478...v480)

- Fixed a crash when the map location layer was read before it finished activating

## v478 (2026-07-11)

[diff](https://github.com/henrik242/where/compare/v465...v478)

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

[diff](https://github.com/henrik242/where/compare/v464...v465)

- Confirmation dialog before closing a viewed track
- Renamed "route" to "track" in the remaining UI strings

## v464 (2026-07-07)

[diff](https://github.com/henrik242/where/compare/v437...v464)

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

[diff](https://github.com/henrik242/where/compare/v436...v437)

- Example coordinates shown in the settings format dropdown

## v436 (2026-05-26)

[diff](https://github.com/henrik242/where/compare/v433...v436)

- Fixed iOS settings: coordinate format selection and URL import wiring

## v433 (2026-05-22)

[diff](https://github.com/henrik242/where/compare/v423...v433)

- Fixed crash when opening single-point tracks
- Improved the live-tracking landing page and added a geocoding cache on the web server
- Security hardening: Android notification intents restricted to the app, web XSS and
  prototype-pollution fixes

## v423 (2026-05-08)

[diff](https://github.com/henrik242/where/compare/v414...v423)

- Improved GPS responsiveness in low-signal terrain
- Internal: web server restructured into server/client/shared modules; fixed iOS builds
  from Android Studio

## v414 (2026-04-26)

[diff](https://github.com/henrik242/where/compare/v406...v414)

- Time-limited live-location sharing
- "Direkte" (live) chip on the map replaces the inline live-tracking toggle
- Two-finger distance line stays anchored to the map for 15 seconds; fixed a line offset
  on iOS

## v406 (2026-04-13)

[diff](https://github.com/henrik242/where/compare/v398...v406)

- Coordinate grid overlay with MGRS cell labels and UTM zone boundaries
- Coordinate format setting, including DMS
- Map label fonts bundled with the app instead of fetched from a CDN
- Removed the county borders overlay

## v398 (2026-04-10)

[diff](https://github.com/henrik242/where/compare/v397...v398)

- Follow a friend's live track on your own map

## v397 (2026-04-03)

[diff](https://github.com/henrik242/where/compare/v395...v397)

- Path-based live-tracking URLs instead of query parameters
- Mac Catalyst support for proper macOS input handling

## v395 (2026-04-02)

[diff](https://github.com/henrik242/where/compare/v387...v395)

- Two-finger distance measurement on the map
- Viewer count shown while recording and on the live-tracking settings page
- Fixed live-tracking resource leaks and a race condition in the update queue

## v387 (2026-04-01)

[diff](https://github.com/henrik242/where/compare/v384...v387)

- Distance to your position shown in the crosshair info card
- Offline chip moves aside when the compass needle is visible

## v384 (2026-03-29)

[diff](https://github.com/henrik242/where/compare/v381...v384)

- Release-pipeline fixes and an optional submit-for-review step; no user-facing changes

## v381 (2026-03-27)

[diff](https://github.com/henrik242/where/compare/v340...v381)

- First-time info dialog when enabling live tracking
- Fixed live tracking on iOS not starting when enabled while recording
- Automated store deployment for Android and iOS (internal)

## v340 (2026-03-23)

[diff](https://github.com/henrik242/where/compare/v325...v340)

- GPX import from URLs and FIT file import from local files and URLs
- Support for all GPX point types, with an import confirmation dialog
- Fixed crash when tapping the location button before the map was ready
- Sponsor button in settings and on the web about page

## v325 (2026-03-19)

[diff](https://github.com/henrik242/where/compare/v323...v325)

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
