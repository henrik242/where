# AGENTS.md

## Build & Test

- Build: `cd app && ../gradlew assembleDebug`
- Install + launch on one device: `./gradlew :app:runDebug` (`ANDROID_SERIAL` picks the device)
- Test: `cd app && ../gradlew testDebugUnitTest`
- Lint: `cd app && ../gradlew lint`
- Single test: `cd app && ../gradlew testDebugUnitTest --tests "no.synth.where.ClassName"`
- Build shared module: `./gradlew :shared:assembleDebug`
- Shared unit tests (commonTest + jvmTest, on the JVM): `./gradlew :shared:jvmTest`
- Web server: `cd web && bun run dev`
- Web tests: `cd web && bun test`
- Places index for `/api/places`: `cd web && bun run places:dev` (fixture, no osmium) or
  `bun run places:build` (real Geofabrik data, needs osmium-tool)

Note: `cd app && ../gradlew testDebugUnitTest` only covers the `app` module. The shared
suite runs via `:shared:jvmTest` or `:shared:allTests` (adds the iOS simulator, needs a Mac).
Tests that need JDK APIs (files, JDBC SQLite) go in `shared/src/jvmTest`.

## General Code Guidelines

- Make sure features are implemented both in Android and iOS, but keep as much of the implementation
  as shared/common code. Try to avoid ios/android specific code if you can.

## Project Layout (where things go)

Keep all navigation, geometry, and hit-testing math in `commonMain` and unit-test it there; only
map rendering and live-location polling are per-platform. Concretely:

- Navigation logic: `shared/.../data/navigation/` (`TrackNavigator`, `NavigationProgress`)
- Geometry primitives: `shared/.../data/geo/` (`SegmentProjection`, `LatLng`)
- Elevation/track math: `shared/.../data/` (`ElevationProfile`, `TrackUtils`, `TrackCrop`)
- GeoJSON builders (shared layer content): `shared/.../ui/map/MapGeoJson.kt`
- Platform map rendering: `MapRenderUtils.kt` (Android, `shared/src/androidMain`) and
  `iosApp/Where/MapViewFactory.swift` (iOS) — keep their styling constants in sync
- Shared Compose UI: `shared/.../ui/`; platform screens: `app/.../ui/` and `shared/src/iosMain/.../ui/`

## Android App Code Guidelines

- Manual constructor injection, no DI framework
- Dependencies are wired in `WhereApplication` (lazy properties); access via `applicationContext as WhereApplication`
- ViewModels are created with `viewModel { MyViewModel(app.dep1, app.dep2) }` in Composables
- Use Timber for logging, never `println` or `Log.*`

## Writing Style (commits, comments, docs)

- Keep commit messages and code comments brief and to the point. No essays.
- No Co-Authored-By trailers or other AI attribution in commits or PRs.
- Never commit as Claude or another AI identity.
- No long dashes, no emojis.
- Never reply to or resolve PR comments unless explicitly asked to.

## Known Release Facts

- iOS release (`release-ios.yml`, `fastlane ios deploy`) fails at `select_build` with "The specified
  pre-release build could not be added" when a previous version is still Waiting for Review. The new
  release still goes through correctly: the new build is attached and released. Do not claim the old
  build is still attached or that the release is broken; the failure is a spurious error only.
  The deploy lane now sets `reject_if_possible`, so a pending review is cancelled and replaced.
