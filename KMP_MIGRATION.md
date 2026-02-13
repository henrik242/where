# Kotlin Multiplatform Migration Plan

## Goal

Migrate the Android app to Kotlin Multiplatform (KMP) with Compose Multiplatform, sharing as much code as possible between Android and iOS.

## Current state

- 54 Kotlin files, single Android target
- Jetpack Compose UI, Room database, Koin DI, Ktor networking
- MapLibre Android for map rendering and offline tiles
- Play Services for fused location
- Firebase Crashlytics

## Strategy

Prepare the codebase incrementally *before* adding iOS. Each phase keeps the Android app fully functional and testable. The iOS target is only introduced once the shared module is ready.

---

## Phase 1 — Extract common geo types (low effort, high impact) ✅ DONE

Almost every data file imports `org.maplibre.android.geometry.LatLng`. Replacing this with our own type unblocks the entire data layer.

### What was done

1. Created `data/geo/LatLng.kt` with common `LatLng` (haversine `distanceTo()`) and `LatLngBounds` (with `latitudeSpan`/`longitudeSpan`)
2. Created `data/geo/MapLibreConversions.kt` with `toMapLibre()`/`toCommon()` extension functions for both types
3. Replaced all `org.maplibre.android.geometry.LatLng` and `LatLngBounds` imports across the entire codebase (data, UI, service, test files)
4. MapLibre conversion happens at boundaries only: `MapLibreMapView.kt` (click events, camera positions), `MapScreen.kt` (camera operations), `MapDownloadManager.kt` (offline region definition)
5. `MapRenderUtils.kt` needed no changes — it reads lat/lon doubles from common types via property access
6. Removed unused `android.location.Location` import from `MapLibreMapView.kt` (replaced `Location.distanceBetween` with `LatLng.distanceTo`)

**Files created:** `data/geo/LatLng.kt`, `data/geo/MapLibreConversions.kt`

**Files changed:** `Region.kt`, `SavedPoint.kt`, `Track.kt`, `RulerState.kt`, `LatLngSerializer.kt`, `FylkeDataLoader.kt`, `MapStyle.kt`, `PlaceSearchClient.kt`, `GeocodingHelper.kt`, `OnlineTrackingClient.kt`, `SavedPointsRepository.kt`, `TrackRepository.kt`, `MapDownloadManager.kt`, `MapLibreMapView.kt`, `MapScreen.kt`, `MapScreenViewModel.kt`, `LocationTrackingService.kt`, `DownloadScreen.kt`, `SavedPointsScreen.kt`, `TracksScreen.kt`, plus 7 test files

**Result:** The entire data layer and business logic is now free of MapLibre Android imports. Only 3 files in `ui/map/` plus `MapDownloadManager.kt` and `MapLibreConversions.kt` still reference `org.maplibre.android.geometry`.

---

## Phase 2 — Replace OkHttp and org.json with multiplatform libraries (medium effort) ✅ DONE

OkHttp and `org.json` are JVM-only. Replaced with Ktor HTTP client and `kotlinx.serialization.json` tree API.

### What was done

1. Added Ktor client dependencies (`ktor-client-core`, `ktor-client-android`) to version catalog (originally CIO, switched to Android engine to fix TLS certificate errors with Android's network security config)
2. Migrated `GeocodingHelper.kt` — OkHttp GET → Ktor `client.get()`, `JSONObject` parsing → `Json.parseToJsonElement().jsonObject`
3. Migrated `PlaceSearchClient.kt` — OkHttp URL builder → Ktor `url { parameters.append() }`, `JSONObject`/`JSONArray` → `jsonObject`/`jsonArray` tree API
4. Migrated `OnlineTrackingClient.kt` — OkHttp POST/PUT with custom headers → Ktor `client.post()`/`client.put()`, `JSONObject().apply { put() }` → `buildJsonObject { put() }`, `JSONArray` → `buildJsonArray { }`. HMAC signing unchanged.
5. Migrated `MapDownloadManager.kt` — `JSONObject` for metadata building/parsing → `buildJsonObject`/`Json.parseToJsonElement()` (no HTTP changes, JSON-only)
6. Removed OkHttp dependency from `libs.versions.toml` and `build.gradle.kts`

**Not migrated (out of scope):** `FylkeDownloader.kt` uses `java.net.URL` (not OkHttp), will be handled separately.

**Files changed:** `libs.versions.toml`, `build.gradle.kts`, `GeocodingHelper.kt`, `PlaceSearchClient.kt`, `OnlineTrackingClient.kt`, `MapDownloadManager.kt`

**Result:** No `org.json` or `okhttp3` imports remain in the codebase. All HTTP and JSON operations use KMP-compatible libraries.

---

## Phase 3 — Remove Timber, add a common logger (low effort) ✅ DONE

Timber is Android-only. Introduced a thin `Logger` object that wraps Timber, so when KMP is added later it becomes `expect/actual`.

### What was done

1. Created `util/Logger.kt` — `object Logger` with `d()`, `w()`, `e()` methods matching Timber's API signatures, delegating to Timber internally
2. Replaced `import timber.log.Timber` → `import no.synth.where.util.Logger` and `Timber.` → `Logger.` in 15 files
3. Made `HttpClient` injectable (internal) in `GeocodingHelper`, `PlaceSearchClient`, and `OnlineTrackingClient` for testability
4. Added `ktor-client-mock` test dependency
5. Added backwards-compatibility tests for Phase 2 (Ktor/JSON) and Phase 3 (Logger):
   - `GeocodingHelperTest.kt` — Ktor mock engine, JSON response parsing, error handling
   - `PlaceSearchClientTest.kt` — Geonorge response parsing, empty/missing fields
   - `OnlineTrackingClientTest.kt` — JSON body building, HMAC signature headers, request methods
   - `MapDownloadManagerMetadataTest.kt` — pure JSON metadata roundtrip
   - `LoggerTest.kt` — verifies Logger delegates to Timber via planted test Tree

**Only file still importing Timber directly:** `WhereApplication.kt` (for `Timber.plant()` — Android-only forever)

**Files created:** `util/Logger.kt`, 5 test files

**Files changed:** `libs.versions.toml`, `build.gradle.kts`, 15 source files (Timber → Logger), 3 HTTP client files (injectable HttpClient)

---

## Phase 4 — Remove Context from data layer (medium effort) ✅ DONE

Removed `android.content.Context` from all data-layer classes. Context remains in DI wiring (`AppModule.kt`) and Android-specific callers.

### What was done

1. **FylkeDownloader / FylkeDataLoader / RegionsRepository** — replaced `context: Context` parameter with `cacheDir: File` throughout the chain. Callers pass `context.cacheDir` at the call site.
2. **MapStyle** — removed Context entirely. Now accepts `regions: List<Region> = emptyList()` as a parameter instead of calling `RegionsRepository.getRegions(context)` internally. Pure function, no dependencies.
3. **TrackRepository / SavedPointsRepository** — replaced `context: Context` with `filesDir: File`. Uses `File(filesDir, ...)` instead of `File(context.filesDir, ...)`.
4. **UserPreferences / ClientIdManager** — removed file-level `Context.dataStore` delegates and `context: Context` constructor params. Now accept `DataStore<Preferences>` directly. DataStore delegates moved to `AppModule.kt`.
5. **AppModule.kt** — DataStore delegates (`userPrefsDataStore`, `clientPrefsDataStore`) now live here. All providers pass concrete types: `context.filesDir`, `context.cacheDir`, `context.userPrefsDataStore`, `context.clientPrefsDataStore`.
6. **Callers updated** — `WhereApp.kt`, `MapLibreMapView.kt`, `DownloadScreen.kt`, `MapDownloadService.kt` all pass concrete types instead of Context.
7. **MapStyleTest** — removed fake `ContextWrapper`, `setUp`/`tearDown`. Now passes regions directly — pure unit test with zero Android deps.
8. **New tests** — `FylkeDownloaderTest` (TemporaryFolder-based file system test), `UserPreferencesTest` (DataStore defaults and updates), `ClientIdManagerTest` (ID generation, persistence, regeneration).

**Files unchanged:** `MapDownloadManager.kt` (keeps Context — MapLibre SDK requirement), `WhereApplication.kt` (Android Application class)

**Files created:** `FylkeDownloaderTest.kt`, `UserPreferencesTest.kt`, `ClientIdManagerTest.kt`

**Files changed:** `FylkeDownloader.kt`, `FylkeDataLoader.kt`, `RegionsRepository.kt`, `MapStyle.kt`, `TrackRepository.kt`, `SavedPointsRepository.kt`, `UserPreferences.kt`, `ClientIdManager.kt`, `AppModule.kt`, `WhereApp.kt`, `MapLibreMapView.kt`, `DownloadScreen.kt`, `MapDownloadService.kt`, `MapStyleTest.kt`

**Result:** No `android.content.Context` imports remain in the data layer. All data classes accept concrete types (`File`, `DataStore<Preferences>`) making them testable without Android framework and ready for KMP `commonMain`.

---

## Phase 5 — Replace java.* APIs with Kotlin/multiplatform equivalents (low effort) ✅ DONE

Replaced JVM-only APIs with Kotlin stdlib equivalents. No new dependencies needed — Kotlin 2.3 provides everything natively.

### What was done

1. **`Track.kt`** — `java.text.SimpleDateFormat`/`java.util.Date` → `kotlin.time.Instant` for ISO 8601 formatting/parsing. `java.util.UUID` → `kotlin.uuid.Uuid`. `Instant` is strictly better: handles fractional seconds and timezone offsets in GPX input, preserves millisecond precision in output.
2. **`RulerState.kt`** — `java.util.UUID.randomUUID()` → `kotlin.uuid.Uuid.random()` with `@OptIn(ExperimentalUuidApi::class)`
3. **`SavedPointsRepository.kt`** — same UUID replacement as above
4. **`HmacUtils.kt`** — `android.util.Base64` → `kotlin.io.encoding.Base64.Default.encode()`. `javax.crypto.Mac`/`SecretKeySpec` stay — they're JVM standard library (not Android-specific), will become expect/actual in Phase 7.
5. **`FylkeDownloader.kt`** — no changes. `java.net.URL` and `java.util.zip.ZipInputStream` stay as-is, will become `actual` implementations in Phase 7.

**New tests:** `HmacUtilsTest.kt` (pins HMAC output with known value — previously impossible due to android.util.Base64 requiring Robolectric), 3 new tests in `TrackTest.kt` (fractional-second parsing, timezone offset parsing, fractional-second roundtrip).

**Files created:** `HmacUtilsTest.kt`

**Files changed:** `Track.kt`, `RulerState.kt`, `SavedPointsRepository.kt`, `HmacUtils.kt`, `TrackTest.kt`

**Result:** No `android.util.Base64`, `java.text.SimpleDateFormat`, `java.util.Date`, or `java.util.UUID` imports remain in the data layer. Only `java.io.File` (Phase 7), `java.net.URL`/`java.util.zip` (Phase 7), and `javax.crypto` (Phase 7) remain.

---

## Phase 6 — Replace Hilt with Koin (medium effort) ✅ DONE

Hilt is Android-only. Koin has full KMP support and uses pure Kotlin DSL instead of annotation processing.

### What was done

1. **Dependencies** — Removed Hilt version, plugin, and 3 library entries (`hilt-android`, `hilt-compiler`, `hilt-navigation-compose`). Added Koin BOM 4.1.1 + 3 library entries (`koin-android`, `koin-androidx-compose`, `koin-test-junit4`). KSP plugin stays for Room.
2. **`di/AppModule.kt`** — Full rewrite from Hilt `@Module`/`@Provides`/`@Singleton` object to Koin `module { }` val with `single { }` and `viewModel { }` DSL. DataStore delegate extensions unchanged.
3. **`WhereApplication.kt`** — Removed `@HiltAndroidApp`, added `startKoin { androidContext(); modules(appModule) }` in `onCreate()`.
4. **`MainActivity.kt`** — Removed `@AndroidEntryPoint` annotation and import.
5. **5 ViewModels** — Removed `@HiltViewModel` and `@Inject` annotations/imports from `WhereAppViewModel`, `MapScreenViewModel`, `TracksScreenViewModel`, `SavedPointsScreenViewModel`, `OnlineTrackingScreenViewModel`.
6. **`LocationTrackingService.kt`** — Replaced `@AndroidEntryPoint` + `@Inject lateinit var` with `KoinComponent` interface + `by inject()` lazy delegates.
7. **`MapDownloadService.kt`** — Removed `@AndroidEntryPoint` (had no injected fields).
8. **5 Composable screens** — Swapped `hiltViewModel()` → `koinViewModel()` import and call in `WhereApp`, `MapScreen`, `TracksScreen`, `SavedPointsScreen`, `OnlineTrackingScreen`.
9. **New test** — `KoinModuleCheckTest.kt` verifies all Koin definitions resolve correctly using `verify()`.

**Files created:** `di/KoinModuleCheckTest.kt`

**Files changed:** `libs.versions.toml`, `build.gradle.kts` (root), `app/build.gradle.kts`, `di/AppModule.kt`, `WhereApplication.kt`, `MainActivity.kt`, `WhereAppViewModel.kt`, `ui/MapScreenViewModel.kt`, `ui/TracksScreenViewModel.kt`, `ui/SavedPointsScreenViewModel.kt`, `ui/OnlineTrackingScreenViewModel.kt`, `service/LocationTrackingService.kt`, `service/MapDownloadService.kt`, `WhereApp.kt`, `ui/MapScreen.kt`, `ui/TracksScreen.kt`, `ui/SavedPointsScreen.kt`, `ui/OnlineTrackingScreen.kt`

**Result:** No Hilt/Dagger imports remain in the codebase. All DI uses Koin, which is fully KMP-compatible.

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
| `org.jetbrains.kotlinx:kotlinx-datetime` | `java.text.SimpleDateFormat`, `java.util.Date` | Yes (not needed — using `kotlin.time.Instant` from stdlib) |
| `org.jetbrains.compose:compose-*` | Jetpack Compose (for shared UI) | Yes |
| `androidx.room:room-*` | (keep) | Yes (since 2.7.0) |
| `androidx.datastore:datastore-*` | (keep) | Yes (since 1.1.0) |
| `org.maplibre.gl:android-sdk` | (keep, androidMain only) | No — platform-specific |

## Dependencies to remove

| Library | Replaced by |
|---|---|
| `com.squareup.okhttp3:okhttp` | Ktor (done in Phase 2) |
| `com.google.dagger:hilt-*` | Koin |
| `com.jakewharton.timber:timber` | Common Logger |
