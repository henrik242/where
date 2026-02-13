# Kotlin Multiplatform Migration Plan

## Goal

Migrate the Android app to Kotlin Multiplatform (KMP) with Compose Multiplatform, sharing as much code as possible between Android and iOS.

## Current state

- 54 Kotlin files, single Android target
- Jetpack Compose UI, Room database, Hilt DI, OkHttp networking
- MapLibre Android for map rendering and offline tiles
- Play Services for fused location
- Firebase Crashlytics

## Strategy

Prepare the codebase incrementally *before* adding iOS. Each phase keeps the Android app fully functional and testable. The iOS target is only introduced once the shared module is ready.

---

## Phase 1 — Extract common geo types (low effort, high impact)

Almost every data file imports `org.maplibre.android.geometry.LatLng`. Replacing this with our own type unblocks the entire data layer.

### Steps

1. Create `data/LatLng.kt` with a simple data class:
   ```kotlin
   data class LatLng(val latitude: Double, val longitude: Double) {
       fun distanceTo(other: LatLng): Double { /* haversine */ }
   }
   data class LatLngBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
       val latitudeSpan get() = north - south
       val longitudeSpan get() = east - west
   }
   ```
2. Replace all `org.maplibre.android.geometry.LatLng` imports with the new type
3. Add conversion extensions in the map layer: `fun LatLng.toMapLibre()` and `fun MLLatLng.toCommon()`
4. Update `LatLngSerializer.kt` to serialize the new type

**Files touched:** `Region.kt`, `SavedPoint.kt`, `Track.kt`, `RulerState.kt`, `LatLngSerializer.kt`, `FylkeDataLoader.kt`, `MapStyle.kt`, `PlaceSearchClient.kt`, `GeocodingHelper.kt`, `OnlineTrackingClient.kt`, `MapRenderUtils.kt`, `MapLibreMapView.kt`, `MapScreenViewModel.kt`

**Why first:** This is the single highest-impact change. It decouples the data layer from MapLibre Android and is straightforward find-and-replace with conversion functions at the boundaries.

---

## Phase 2 — Replace OkHttp and org.json with multiplatform libraries (medium effort)

OkHttp and `org.json` are JVM-only. Replace with Ktor and `kotlinx.serialization.json`.

### Steps

1. Add Ktor client dependency (CIO engine for now, swap to Darwin engine later for iOS)
2. Migrate `PlaceSearchClient.kt` — replace OkHttp calls with Ktor, `JSONObject` parsing with `kotlinx.serialization.json`
3. Migrate `GeocodingHelper.kt` — same pattern
4. Migrate `OnlineTrackingClient.kt` — same pattern, keep HMAC signing separate
5. Migrate `FylkeDownloader.kt` — replace `java.net.URL` with Ktor for download, keep ZIP extraction for now
6. Remove OkHttp and org.json dependencies

**Files touched:** `PlaceSearchClient.kt`, `GeocodingHelper.kt`, `OnlineTrackingClient.kt`, `FylkeDownloader.kt`, `build.gradle.kts`

---

## Phase 3 — Remove Timber, add a common logger (low effort)

Timber is Android-only. Introduce a thin logging interface.

### Steps

1. Create `util/Logger.kt`:
   ```kotlin
   expect object Logger {
       fun d(tag: String, message: String)
       fun e(tag: String, message: String, throwable: Throwable? = null)
   }
   ```
   For now, the single Android `actual` just delegates to Timber.
2. Replace all `Timber.d(...)` / `Timber.e(...)` calls

**Files touched:** ~15 files that import Timber

---

## Phase 4 — Remove Context from data layer (medium effort)

Many data classes take `android.content.Context` just for file access or DataStore. Abstract this away.

### Steps

1. **RegionsRepository / FylkeDataLoader** — instead of passing Context, pass a `cacheDir: File` or a `FileSystem` interface
2. **FylkeDownloader** — extract a `DownloadCache` interface: `fun getCachedFile(): ByteArray?`, `fun save(data: ByteArray)`, `fun hasCachedData(): Boolean`
3. **MapStyle** — pass `regions: List<Region>` directly instead of calling `RegionsRepository.getRegions(context)` internally
4. **ClientIdManager / UserPreferences** — DataStore supports KMP since 1.1.0, but the `Context.dataStore` delegate is Android-only. Create the DataStore with `PreferenceDataStoreFactory.createWithPath()` and pass the path from platform code.

**Files touched:** `RegionsRepository.kt`, `FylkeDataLoader.kt`, `FylkeDownloader.kt`, `MapStyle.kt`, `UserPreferences.kt`, `ClientIdManager.kt`, `AppModule.kt`

---

## Phase 5 — Replace java.* APIs with Kotlin/multiplatform equivalents (low effort)

### Steps

1. `java.text.SimpleDateFormat` / `java.util.Date` in `Track.kt` → `kotlinx.datetime`
2. `java.util.UUID` in `Track.kt`, `RulerState.kt` → `kotlin.uuid.Uuid` (Kotlin 2.0+) or a simple `expect fun randomUuid(): String`
3. `java.util.zip.ZipInputStream` in `FylkeDownloader.kt` → keep as `actual` on Android, implement with Foundation on iOS later
4. `android.util.Base64` / `javax.crypto.*` in `HmacUtils.kt` → `expect`/`actual` (CommonCrypto on iOS)

**Files touched:** `Track.kt`, `RulerState.kt`, `FylkeDownloader.kt`, `HmacUtils.kt`

---

## Phase 6 — Replace Hilt with Koin (medium effort)

Hilt is Android-only. Koin has full KMP support.

### Steps

1. Add Koin dependencies
2. Convert `AppModule.kt` from Hilt `@Module` to Koin `module { }` DSL
3. Remove `@HiltViewModel` from all ViewModels, use Koin's `koinViewModel()`
4. Remove `@AndroidEntryPoint` from `MainActivity`, `@HiltAndroidApp` from `WhereApplication`
5. Initialize Koin in `WhereApplication.onCreate()`
6. Remove Hilt dependencies

**Files touched:** `AppModule.kt`, `WhereApplication.kt`, `MainActivity.kt`, all 5 ViewModels, all screens that call `hiltViewModel()`

---

## Phase 7 — Set up KMP project structure (medium effort)

At this point the codebase is ready for multiplatform. No iOS code yet, just the project restructure.

### Steps

1. Create `shared/` module with `build.gradle.kts`:
   ```kotlin
   kotlin {
       androidTarget()
       listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
           it.binaries.framework { baseName = "Shared" }
       }
       sourceSets {
           commonMain.dependencies { /* ktor, koin, room, kotlinx libs */ }
           androidMain.dependencies { /* ktor-android, timber, etc */ }
           iosMain.dependencies { /* ktor-darwin */ }
       }
   }
   ```
2. Move all prepared code into `shared/src/commonMain/`
3. Move Android `actual` implementations into `shared/src/androidMain/`
4. Create stub `actual` implementations in `shared/src/iosMain/` (can throw `TODO()` initially)
5. Update `app/build.gradle.kts` to depend on `shared` module
6. Verify Android app still builds and runs

---

## Phase 8 — Move Compose UI to shared (medium effort)

Compose Multiplatform supports iOS. Most UI screens can move to commonMain.

### Shared directly (pure Compose, no platform APIs)

- `ui/map/MapDialogs.kt`
- `ui/map/MapFabColumn.kt`
- `ui/map/MapOverlays.kt`
- `ui/map/SearchOverlay.kt`
- `ui/map/MapScreenContent.kt`
- `ui/theme/Theme.kt`

### Shared with expect/actual for platform actions

| Screen | Platform APIs needed |
|---|---|
| `SettingsScreen.kt` | `expect fun setAppLocale(tag: String?)` |
| `SavedPointsScreen.kt` | Color parsing (`expect fun parseColorInt(hex: String): Int`) |
| `OnlineTrackingScreen.kt` | `expect fun shareText(...)`, `expect fun openUrl(...)` |
| `TracksScreen.kt` | `expect fun shareFile(...)`, `expect fun pickFile(...)`, `expect fun saveToDownloads(...)` |
| `DownloadScreen.kt` | Download service trigger |
| `MapScreen.kt` | Permission handling, location service start/stop |

### Stays in androidMain / iosMain

| File | Reason |
|---|---|
| `ui/map/MapLibreMapView.kt` | `AndroidView` wrapping MapLibre Android → `UIKitView` wrapping MapLibre iOS |
| `ui/map/MapRenderUtils.kt` | MapLibre Android style/layer/source APIs |

---

## Phase 9 — Create iOS target (large effort)

### Steps

1. Create Xcode project, import `Shared.xcframework`
2. Implement all `actual` declarations in `shared/src/iosMain/`:
   - `Logger` → `os_log` or `print`
   - `HmacUtils` → CommonCrypto
   - `FileSystem` / cache access → Foundation `FileManager`
   - `ZipExtractor` → Foundation or a Swift ZIP library
   - Platform actions (share, open URL, pick file) → UIKit APIs
3. Implement `MapLibreMapView` for iOS using MapLibre Native iOS SDK in a `UIKitView`
4. Implement `MapRenderUtils` for iOS using MapLibre iOS style API
5. Implement location tracking using CoreLocation (no foreground service concept — use background location modes in `Info.plist`)
6. Implement offline map downloads using MapLibre iOS offline API
7. Wire up the Compose Multiplatform UI as the app's root view

---

## What stays Android-only forever

| File | Reason |
|---|---|
| `MainActivity.kt` | Android entry point |
| `WhereApplication.kt` | Android `Application` subclass, Firebase init |
| `service/LocationTrackingService.kt` | Android foreground service with notification |
| `service/MapDownloadService.kt` | Android foreground service for tile downloads |

These have iOS counterparts but share no code.

---

## Recommended order and rationale

Phases 1-5 are **pure refactoring** — the app stays Android-only and every change is testable against the existing build. They remove platform coupling from the data and utility layers without adding any new infrastructure.

Phase 6 (Hilt → Koin) is the most invasive single change but is mechanical.

Phase 7 is the actual KMP setup — done only after the code is ready.

Phase 8 moves UI to shared — relatively smooth since Compose Multiplatform follows the same API.

Phase 9 is where the real iOS work begins, but by then ~80% of the code is already shared.

---

## Dependencies to add

| Library | Replaces | KMP support |
|---|---|---|
| `io.ktor:ktor-client-*` | OkHttp | Yes |
| `io.insert-koin:koin-*` | Hilt | Yes |
| `org.jetbrains.kotlinx:kotlinx-datetime` | `java.text.SimpleDateFormat`, `java.util.Date` | Yes |
| `org.jetbrains.compose:compose-*` | Jetpack Compose (for shared UI) | Yes |
| `androidx.room:room-*` | (keep) | Yes (since 2.7.0) |
| `androidx.datastore:datastore-*` | (keep) | Yes (since 1.1.0) |
| `org.maplibre.gl:android-sdk` | (keep, androidMain only) | No — platform-specific |

## Dependencies to remove

| Library | Replaced by |
|---|---|
| `com.squareup.okhttp3:okhttp` | Ktor |
| `com.google.dagger:hilt-*` | Koin |
| `com.jakewharton.timber:timber` | Common Logger |
