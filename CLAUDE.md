# CLAUDE.md

## Build & Test

- Build: `cd app && ../gradlew assembleDebug`
- Test: `cd app && ../gradlew testDebugUnitTest`
- Lint: `cd app && ../gradlew lint`
- Single test: `cd app && ../gradlew testDebugUnitTest --tests "no.synth.where.ClassName"`
- Build shared module: `./gradlew :shared:assembleDebug`
- Shared unit tests (commonTest, runs on the JVM): `./gradlew :shared:testAndroidHostTest`
- Web server: `cd web && bun run dev`
- Web tests: `cd web && bun test`

Note: `cd app && ../gradlew testDebugUnitTest` only covers the `app` module. The shared
`commonTest` suite runs via `:shared:testAndroidHostTest` (host/JVM) or `:shared:allTests`
(adds the iOS simulator, needs a Mac).

## General Code Guidelines

- Maintain good test coverage — write tests for new logic
- Keep code clean and concise; no needless comments or boilerplate
- Make sure features are implemented both in Android and iOS, but keep as much of the implementation
  as shared/common code. Try to avoid ios/android specific code if you can.

## Project Layout (where things go)

Keep all navigation, geometry, and hit-testing math in `commonMain` and unit-test it there; only
map rendering and live-location polling are per-platform. Concretely:

- Navigation logic: `shared/.../data/navigation/` (`TrackNavigator`, `NavigationProgress`)
- Geometry primitives: `shared/.../data/geo/` (`SegmentProjection`, `LatLng`)
- Elevation/track math: `shared/.../data/` (`ElevationProfile`, `TrackUtils`)
- GeoJSON builders (shared layer content): `shared/.../ui/map/MapGeoJson.kt`
- Platform map rendering: `MapRenderUtils.kt` (Android, `shared/src/androidMain`) and
  `iosApp/Where/MapViewFactory.swift` (iOS) — keep their styling constants in sync
- Shared Compose UI: `shared/.../ui/`; platform screens: `app/.../ui/` and `shared/src/iosMain/.../ui/`

## Android App Code Guidelines

- Follow Android design guidelines and Material 3 best practices
- Kotlin with Jetpack Compose, manual constructor injection (no DI framework), Room for local storage
- Dependencies are wired in `WhereApplication` (lazy properties); access via `applicationContext as WhereApplication`
- ViewModels are created with `viewModel { MyViewModel(app.dep1, app.dep2) }` in Composables
- Use Timber for logging, never `println` or `Log.*`
