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

## Example agent prompt:

proceed with kmp phase X. update and improve the migration guide as you go. make sure things are backwards compatible using relevant tests.
be clean, simple and intuitive, add tests, and prefer best practises (that also applies to previous phases, if relevant).
prefer shared code over ios/android-specific code.

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

## Phase 7 — Set up shared KMP module & move data layer (medium effort) ✅ DONE

Created the `shared/` KMP module and moved the prepared data layer into it.

### What was done

1. **Gradle configuration** — Added `kotlin-multiplatform`, `android-kotlin-multiplatform-library` (AGP 9 requires this instead of `android-library`), and `room` plugins to version catalog. Created `shared/build.gradle.kts` with `androidLibrary {}` block (AGP 9 KMP DSL), iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`), and `commonMain`/`androidMain`/`iosMain` source sets. Added `ktor-client-darwin` dependency for iOS. Added `implementation(project(":shared"))` to app module.
2. **Expect/actual declarations** — Created 6 expect declarations in `commonMain` (`Platform.kt`, `Logger.kt`, `HmacUtils.kt`, `DeviceUtils.kt`, `PlatformFile.kt`, `HttpClientFactory.kt`) with matching actual implementations in `androidMain` and TODO stubs in `iosMain`.
3. **Moved 22 files to commonMain** — Pure data types (LatLng, Region, RulerState, etc.), Room entities/DAOs/database, repositories, HTTP clients, DataStore files, MapStyle, and utilities. Applied modifications: `System.currentTimeMillis()` → `currentTimeMillis()`, `java.io.File` → `PlatformFile`, `HttpClient(Android)` → `createDefaultHttpClient()`, `BuildConfig.TRACKING_HMAC_SECRET` → constructor parameter.
4. **Moved 6 files to androidMain** — FylkeDownloader, FylkeDataLoader, RegionsRepository, StyleServer, MapDownloadManager, MapLibreConversions (heavy JVM/Android deps).
5. **Updated app module** — `AppModule.kt` wraps `filesDir` in `PlatformFile()`. `LocationTrackingService.kt` passes `BuildConfig.TRACKING_HMAC_SECRET` to `OnlineTrackingClient`.
6. **Updated tests** — `OnlineTrackingClientTest.kt` adds `hmacSecret` param, removes Robolectric. `KoinModuleCheckTest.kt` uses `PlatformFile::class` instead of `java.io.File::class`.

**Files created:** `shared/build.gradle.kts`, 18 expect/actual/stub files

**Files moved to commonMain:** `LatLng.kt`, `Region.kt`, `RulerState.kt`, `LatLngSerializer.kt`, `MapState.kt`, `Track.kt`, `SavedPoint.kt`, `SavedPointEntity.kt`, `TrackEntity.kt`, `TrackPointEntity.kt`, `TrackDao.kt`, `SavedPointDao.kt`, `WhereDatabase.kt`, `TrackRepository.kt`, `SavedPointsRepository.kt`, `OnlineTrackingClient.kt`, `GeocodingHelper.kt`, `PlaceSearchClient.kt`, `UserPreferences.kt`, `ClientIdManager.kt`, `MapStyle.kt`, `NamingUtils.kt`, `GeoExtensions.kt`

**Files moved to androidMain:** `FylkeDownloader.kt`, `FylkeDataLoader.kt`, `RegionsRepository.kt`, `StyleServer.kt`, `MapDownloadManager.kt`, `MapLibreConversions.kt`

**Files changed:** `libs.versions.toml`, root `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `AppModule.kt`, `LocationTrackingService.kt`, `OnlineTrackingClientTest.kt`, `KoinModuleCheckTest.kt`

---

## Phase 8 — Move Compose UI to shared (medium effort) ✅ DONE (part 1)

Added Compose Multiplatform to the shared module and moved the pure-Compose UI files.

### What was done

1. **Compose Multiplatform plugin** — Added `org.jetbrains.compose` (1.10.1) and `org.jetbrains.kotlin.plugin.compose` plugins to the shared module. Added `compose.material3`, `compose.materialIconsExtended`, and `compose.components.resources` dependencies to `commonMain`.
2. **Compose Multiplatform resources** — Created `shared/src/commonMain/composeResources/values/strings.xml` with all string resources used by the moved UI files. Configured `publicResClass = true` with package `no.synth.where.resources`.
3. **Color utility** — Created `util/ColorUtils.kt` with `parseHexColor(hex: String): Color` to replace Android-only `toColorInt()` from `androidx.core.graphics`.
4. **Moved 6 files to commonMain** — `Theme.kt`, `MapDialogs.kt`, `MapFabColumn.kt`, `MapOverlays.kt`, `SearchOverlay.kt`, `MapScreenContent.kt`. Updated all to use Compose MP `stringResource(Res.string.xxx)` instead of Android `stringResource(R.string.xxx)`, and `parseHexColor()` instead of `toColorInt()`.
5. **New test** — `ColorUtilsTest.kt` covering 6-digit, 8-digit, with/without hash, black/white, and invalid input.

**Files created:** `shared/build.gradle.kts` (updated), `shared/src/commonMain/composeResources/values/strings.xml`, `shared/src/commonMain/kotlin/no/synth/where/util/ColorUtils.kt`, `ColorUtilsTest.kt`

**Files moved to shared commonMain:** `ui/theme/Theme.kt`, `ui/map/MapDialogs.kt`, `ui/map/MapFabColumn.kt`, `ui/map/MapOverlays.kt`, `ui/map/SearchOverlay.kt`, `ui/map/MapScreenContent.kt`

**Files changed:** `libs.versions.toml` (added `composeMultiplatform` version and plugin), root `build.gradle.kts` (added compose plugins), `shared/build.gradle.kts` (added Compose MP plugins and dependencies)

**Result:** 6 Compose UI files are now in `commonMain` and will work on both Android and iOS. String resources use Compose Multiplatform's resource system. The app module's `strings.xml` retains all strings for backward compatibility with app-level files that still use `R.string.*`.

---

## Phase 8 Part 2 — Move remaining screen content composables to shared ✅ DONE

Moved the content composables from the remaining 5 screens to `shared/src/commonMain`. Each screen follows the pattern of `XxxScreen()` (wrapper with ViewModel + platform APIs) calling `XxxScreenContent()` (pure Compose). The content was moved to shared; wrappers stay in app.

### What was done

1. **Fixed Phase 5 leftovers** — `Track.kt`: replaced remaining `System.currentTimeMillis()` with `currentTimeMillis()` from `no.synth.where.util`
2. **Created `DateTimeUtils` expect/actual** — `expect fun formatDateTime(epochMillis: Long, pattern: String): String` in commonMain with `SimpleDateFormat`-based actual in androidMain and TODO stub in iosMain. Replaces `SimpleDateFormat` usage in TracksScreen's `formatTrackInfo` and MapScreen's date formatting.
3. **Extracted `RegionTileInfo`** — Moved from nested class in `MapDownloadManager` to top-level data class in `shared/src/commonMain/kotlin/no/synth/where/data/RegionTileInfo.kt`
4. **Extracted `formatBytes` utility** — Created `shared/src/commonMain/kotlin/no/synth/where/util/FormatUtils.kt` replacing duplicated local functions in `DownloadScreen.kt`
5. **Added string resources** — All strings referenced by the 5 content composables added to `shared/src/commonMain/composeResources/values/strings.xml`
6. **Moved content composables to shared** — Created 5 new files in `shared/src/commonMain/kotlin/no/synth/where/ui/`:
   - `SavedPointsScreenContent.kt` — `SavedPointsScreenContent`, `SavedPointItem`, `EditPointDialog`
   - `OnlineTrackingScreenContent.kt` — `OnlineTrackingScreenContent`
   - `SettingsScreenContent.kt` — `SettingsScreenContent`, `LanguageOption` data class
   - `TracksScreenContent.kt` — `TracksScreenContent`, `TrackItem`, `formatTrackInfo`
   - `DownloadScreenContent.kt` — `DownloadScreenContent`, `LayerRegionsScreenContent`, `LayerInfo`
7. **Slimmed app wrappers** — Each screen file in app now contains only the wrapper composable and platform-specific functions
8. **Updated MapScreen.kt** — Replaced `SimpleDateFormat` with `formatDateTime()` from shared utils
9. **Added tests** — `DateTimeUtilsTest`, `FormatUtilsTest`, `ScreenContentTest` (parameter documentation + data class tests)

**Files created:**
- `shared/src/commonMain/kotlin/no/synth/where/util/DateTimeUtils.kt`
- `shared/src/androidMain/kotlin/no/synth/where/util/DateTimeUtils.kt`
- `shared/src/iosMain/kotlin/no/synth/where/util/DateTimeUtils.kt`
- `shared/src/commonMain/kotlin/no/synth/where/data/RegionTileInfo.kt`
- `shared/src/commonMain/kotlin/no/synth/where/util/FormatUtils.kt`
- `shared/src/commonMain/kotlin/no/synth/where/ui/SavedPointsScreenContent.kt`
- `shared/src/commonMain/kotlin/no/synth/where/ui/OnlineTrackingScreenContent.kt`
- `shared/src/commonMain/kotlin/no/synth/where/ui/SettingsScreenContent.kt`
- `shared/src/commonMain/kotlin/no/synth/where/ui/TracksScreenContent.kt`
- `shared/src/commonMain/kotlin/no/synth/where/ui/DownloadScreenContent.kt`
- `app/src/test/java/no/synth/where/util/DateTimeUtilsTest.kt`
- `app/src/test/java/no/synth/where/util/FormatUtilsTest.kt`
- `app/src/test/java/no/synth/where/ui/ScreenContentTest.kt`

**Files changed:** `Track.kt`, `MapDownloadManager.kt` (removed nested RegionTileInfo), `strings.xml` (shared), `SavedPointsScreen.kt`, `OnlineTrackingScreen.kt`, `SettingsScreen.kt`, `TracksScreen.kt`, `DownloadScreen.kt`, `MapScreen.kt`

**Result:** All screen content composables are now in `commonMain` and will work on both Android and iOS. Only platform wrappers (ViewModel, Intent, file picker, location services) remain in the app module.

### Stays in androidMain / iosMain

| File | Reason |
|---|---|
| `ui/map/MapLibreMapView.kt` | `AndroidView` wrapping MapLibre Android → `UIKitView` wrapping MapLibre iOS |
| `ui/map/MapRenderUtils.kt` | MapLibre Android style/layer/source APIs |

---

## Phase 9 — Move FylkeDownloader/FylkeDataLoader/RegionsRepository to commonMain ✅ DONE

Moved the county data download/parse/cache chain from `shared/src/androidMain` to `shared/src/commonMain`. These files were ~90% pure Kotlin logic with only three JVM dependencies: `java.net.URL` (HTTP), `java.util.zip.ZipInputStream` (ZIP), and `java.io.File` (file I/O).

### What was done

1. **Extended PlatformFile** — Added `lastModified(): Long`, `writeBytes(ByteArray)`, `length(): Long` to expect class in commonMain. Android actual delegates to `java.io.File`. iOS actual has TODO stubs.
2. **Created ZipUtils expect/actual** — `extractFirstFileFromZip(zipData: ByteArray, extension: String): ByteArray?` in commonMain. Android actual uses `java.util.zip.ZipInputStream`. iOS actual has TODO stub.
3. **Moved FylkeDataLoader** — `java.io.File` → `PlatformFile`. Data classes (`FylkeGeoJSON`, `FylkeFeature`, `FylkeGeometry`) moved along unchanged — all pure `kotlinx.serialization`.
4. **Moved FylkeDownloader** — Replaced `java.net.URL` with Ktor `HttpClient.get().readRawBytes()`, `ZipInputStream` with `extractFirstFileFromZip()`, `java.io.File` with `PlatformFile`, `System.currentTimeMillis()` with `currentTimeMillis()`, `Dispatchers.IO` with `Dispatchers.Default`.
5. **Moved RegionsRepository** — `java.io.File` → `PlatformFile`. No other changes.
6. **Updated callers** — `WhereApp.kt`, `MapLibreMapView.kt`, `DownloadScreen.kt`, `MapDownloadService.kt` now wrap `context.cacheDir` in `PlatformFile()`.
7. **Updated tests** — `FylkeDownloaderTest.kt` wraps `tempFolder.root` in `PlatformFile()`. `FylkeGeoJSONTest.kt` unchanged (tests pure string parsing).

**Files created:** `shared/src/commonMain/.../util/ZipUtils.kt`, `shared/src/androidMain/.../util/ZipUtils.kt`, `shared/src/iosMain/.../util/ZipUtils.kt`

**Files moved to commonMain:** `FylkeDownloader.kt`, `FylkeDataLoader.kt` (with `FylkeGeoJSON`, `FylkeFeature`, `FylkeGeometry`), `RegionsRepository.kt`

**Files changed:** `PlatformFile.kt` (commonMain, androidMain, iosMain), `WhereApp.kt`, `MapLibreMapView.kt`, `DownloadScreen.kt`, `MapDownloadService.kt`, `FylkeDownloaderTest.kt`

**Result:** The entire county data pipeline is now in `commonMain`. Only `androidMain` data files remaining are `StyleServer.kt`, `MapDownloadManager.kt`, and `MapLibreConversions.kt` (all have hard MapLibre Android dependencies).

---

## Phase 10 — iOS target MVP (large effort) ✅ DONE

Implemented a working iOS app with map viewing, screen navigation, and settings. Track recording, offline downloads, and online tracking actions are deferred.

### Scope

- Map viewing with Kartverket/OSM/OpenTopoMap layers
- Navigation to all screens (settings, tracks, saved points, online tracking)
- "My location" on map
- Theme setting (system/light/dark)
- Delete/rename tracks and saved points from their screens

**Deferred:** track recording, offline map downloads, online tracking, GPX import/export, Firebase Crashlytics, county borders (ZipUtils returns null)

### What was done

1. **Implemented 7 iOS actual stubs** in `shared/src/iosMain/`:
   - `Platform.kt` — `NSDate().timeIntervalSince1970 * 1000`
   - `Logger.kt` — `println` with level prefix (`D:`, `W:`, `E:`), `%s` format argument support
   - `HmacUtils.kt` — `CCHmac` via `platform.CoreCrypto` with `ByteArray.usePinned` for zero-copy, Base64 encoding via `kotlin.io.encoding`
   - `DateTimeUtils.kt` — `NSDateFormatter` with `NSLocale.currentLocale`
   - `DeviceUtils.kt` — `NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"]` check
   - `PlatformFile.kt` — `NSFileManager` for exists/rename/lastModified/length, `NSString` for readText/resolve/path operations, `NSData.create` for writeBytes with auto parent directory creation
   - `ZipUtils.kt` — Returns `null` (county borders deferred)

2. **Configured Room for iOS:**
   - Added KSP tasks for `kspIosX64`, `kspIosArm64`, `kspIosSimulatorArm64`
   - Added `sqlite-bundled` (2.5.0) dependency to `iosMain`
   - Added `@ConstructedBy(WhereDatabaseConstructor::class)` annotation to `WhereDatabase`
   - Added `expect object WhereDatabaseConstructor : RoomDatabaseConstructor<WhereDatabase>` in commonMain
   - Created `DatabaseBuilder.kt` in iosMain — `Room.databaseBuilder` with `BundledSQLiteDriver` and `NSDocumentDirectory` path

3. **Configured DataStore for iOS:**
   - Created `DataStoreFactory.kt` — `PreferenceDataStoreFactory.createWithPath` using `NSDocumentDirectory`

4. **Created Koin iOS module:**
   - `IosModule.kt` — Registers Room database, DAOs, repositories (with `PlatformFile(documentsDir)`), `UserPreferences`, `ClientIdManager`
   - `KoinHelper.kt` — `fun initKoin()` wrapping `startKoin { modules(iosModule) }`, callable from Swift
   - Added `koin-core` (4.1.1) dependency to `iosMain`

5. **Created iOS UI layer:**
   - `MainViewController.kt` — `ComposeUIViewController { IosApp(mapViewProvider) }` entry point
   - `IosApp.kt` — State-based navigation with `Screen` enum and back stack. Collects `UserPreferences` flows for theme/settings. Wires all shared `*ScreenContent` composables with dialog state management.
   - `MapViewProvider.kt` — Interface with `createMapView()`, `setStyle(json)`, `setCamera(lat, lon, zoom)`, `setShowsUserLocation(show)`. Implemented in Swift.
   - `IosMapScreen.kt` — Wraps `MapScreenContent` with `UIKitView` factory. Manages layer selection, style updates via `MapStyle.getStyle()`, user location toggle.
6. **Created Xcode project:**
   - `iosApp/iosApp.xcodeproj` — iOS 16.0 deployment target, MapLibre iOS SDK via SPM
   - `WhereApp.swift` — Entry point, calls `KoinHelperKt.initKoin()`
   - `ComposeView.swift` — `UIViewControllerRepresentable` wrapping `MainViewControllerKt.MainViewController(mapViewProvider:)`
   - `MapViewFactory.swift` — Swift class implementing `MapViewProvider`, creates `MLNMapView`, writes style JSON to temp file and loads as `file://` URL
   - `Info.plist` — `NSLocationWhenInUseUsageDescription` for location permission
   - Gradle `embedAndSignAppleFrameworkForXcode` build phase to compile and link `Shared.framework`

### Key architectural decisions

1. **MapLibre via Swift bridge** — Rather than complex Kotlin/Native cinterop for MapLibre iOS, a Swift `MapViewFactory` implements a Kotlin `MapViewProvider` interface. Compose `UIKitView` calls through this interface.
2. **Style via temp file** — Android uses `StyleServer` (local HTTP). iOS writes style JSON to a temp file and loads `file://` URL. Simpler, no server needed.
3. **State-based navigation** — Simple `mutableStateOf<Screen>` with back stack list. Shared screen content composables are navigation-agnostic.
4. **Dialog state hoisted** — `TracksScreenContent` and `SavedPointsScreenContent` expect dialog state (trackToDelete, editingPoint, etc.) as parameters. `IosApp.kt` manages this state.

**Files created:**
- 7 iOS actual implementations (replaced TODO stubs)
- `data/db/DatabaseBuilder.kt`, `data/DataStoreFactory.kt` (iosMain)
- `di/IosModule.kt`, `di/KoinHelper.kt` (iosMain)
- `MainViewController.kt`, `IosApp.kt` (iosMain)
- `ui/map/MapViewProvider.kt`, `ui/map/IosMapScreen.kt` (iosMain)
- `iosApp/iosApp/WhereApp.swift`, `ComposeView.swift`, `MapViewFactory.swift`, `Info.plist`
- `iosApp/iosApp.xcodeproj/project.pbxproj`

**Files modified:**
- `shared/build.gradle.kts` — KSP iOS tasks, `sqlite-bundled` + `koin-core` deps
- `gradle/libs.versions.toml` — Added `sqlite-bundled`, `koin-core`
- `shared/.../data/db/WhereDatabase.kt` — `@ConstructedBy` annotation + `WhereDatabaseConstructor` expect

**Verification:**
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL
- `cd app && ../gradlew assembleDebug` — BUILD SUCCESSFUL (no Android regression)
- `cd app && ../gradlew testDebugUnitTest` — BUILD SUCCESSFUL (all tests pass)

### What's missing from iOS (MVP gaps)

| Feature | Status | Notes |
|---|---|---|
| Track recording | Not wired | No `LocationTrackingService` equivalent. Needs CoreLocation + background modes. |
| Offline map downloads | Not wired | No `MapDownloadManager` equivalent. Needs MapLibre iOS offline API. |
| Online tracking (sending) | Not wired | Toggle works but no location data is sent. |
| GPX import/export | Not wired | Buttons are no-ops. Needs `UIDocumentPickerViewController` / `UIActivityViewController`. |
| Place search | Not wired | Search button is a no-op. `GeocodingHelper`/`PlaceSearchClient` are in commonMain and ready. |
| County borders | Deferred | `ZipUtils` returns `null`. Needs a zip library or `NSData`+compression framework. |
| Map track/point rendering | Not implemented | MapLibre iOS style layers for tracks and saved points. |
| Ruler tool | Not wired | Button is a no-op. Needs map tap handling through Swift bridge. |
| Firebase Crashlytics | Not applicable | Would need a separate iOS crash reporting SDK. |
| Language selection | Not shown | iOS handles language via system settings; dropdown omitted. |
| Version info | Hardcoded | Shows "Where iOS MVP" instead of git-derived version. |

---

## Phase 11 — iOS location and track recording ✅ DONE

Wired up location tracking and track recording on iOS, matching the Android UX: tap record, see live distance, tap stop, name the track via reverse geocode, save or discard.

### What was done

1. **Created `IosLocationTracker`** — Kotlin/Native class wrapping `CLLocationManager`, implementing `CLLocationManagerDelegateProtocol`. Handles permission requests, start/stop tracking, and feeds location updates to `TrackRepository.addTrackPoint()` during recording. Uses `useContents` for `CLLocationCoordinate2D` access, `distanceFilter = 5.0`, `kCLLocationAccuracyBest`.
2. **Extended `MapViewProvider`** — Added `updateTrackLine(geoJson, color)`, `clearTrackLine()`, and `getUserLocation()` methods to the Kotlin interface.
3. **Implemented track rendering in `MapViewFactory.swift`** — `MLNMapViewDelegate` conformance, GeoJSON-based track line rendering via `MLNShapeSource` + `MLNLineStyleLayer`, pending track state for style reloads, `UIColor(hex:)` convenience initializer. Fixed map reset on navigation by reusing existing `MLNMapView` instance.
4. **`IosLocationTracker` created directly** — Not registered in Koin because `KClass` for Kotlin subclasses of Obj-C classes (`NSObject`) is not supported by Koin's reflection. Created directly in `IosMapScreen` via `remember { IosLocationTracker(trackRepository) }`.
5. **Rewrote `IosMapScreen`** — Full recording state management: observes `trackRepository.isRecording` and `currentTrack`, stop dialog with auto-resolved track name via `GeocodingHelper.reverseGeocode()` (same "Place1 → Place2" pattern as Android), live distance display, snackbar feedback, track line rendering in `UIKitView` update block.
6. **Created `TrackGeoJson` utility** — Builds GeoJSON LineString string from track points, avoiding Kotlin/Native collection boxing issues.
7. **Updated `IosApp.kt`** — Removed `onMyLocationClick` parameter (now handled internally by `IosMapScreen`).

### Key decisions

- **CLLocationManager in Kotlin/Native** — No Swift bridge needed. `platform.CoreLocation` provides full access.
- **GeoJSON string for track rendering** — Avoids Kotlin/Native collection boxing. Kotlin builds the string, Swift passes it to `MLNShape(data:encoding:)`.
- **getUserLocation() via MapLibre** — Reads `mapView.userLocation.location` instead of running a second CLLocationManager.
- **Foreground-only for MVP** — "When in use" location. No `UIBackgroundModes` needed yet.

**Files created:**
- `shared/src/iosMain/kotlin/no/synth/where/location/IosLocationTracker.kt`
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/TrackGeoJson.kt`

**Files modified:**
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/MapViewProvider.kt` — added 3 methods
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/IosMapScreen.kt` — full rewrite for recording
- `shared/src/iosMain/kotlin/no/synth/where/di/IosModule.kt` — no changes needed (IosLocationTracker created directly)
- `shared/src/iosMain/kotlin/no/synth/where/IosApp.kt` — removed onMyLocationClick param
- `iosApp/iosApp/MapViewFactory.swift` — track rendering, delegate, getUserLocation, map view reuse

**Verification:**
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL
- `cd app && ../gradlew assembleDebug` — BUILD SUCCESSFUL (no Android regression)
- `cd app && ../gradlew testDebugUnitTest` — BUILD SUCCESSFUL (all tests pass)

### Bug fix: map reset on navigation

Fixed a bug where navigating to settings and back would show a blank/default map. The issue was that `createMapView()` created a new `MLNMapView` each time, but `currentStyleJson` still held the old value causing `setStyle()` to skip applying the style. Fix: `createMapView()` now returns the existing map view if one already exists.

---

## Phase 12 — iOS place search, viewing tracks/points on map ✅ DONE

Wired the remaining map screen interactions: place search, "show on map" for tracks and saved points, saved points layer rendering, and viewing track/point banners.

### What was done

1. **Extended `MapViewProvider`** — Added `setCameraBounds(south, west, north, east, padding)` for fitting camera to track bounds, `updateSavedPoints(geoJson)` and `clearSavedPoints()` for rendering saved point circles.
2. **Implemented in `MapViewFactory.swift`** — `setCameraBounds` uses `MLNCoordinateBounds` + `setVisibleCoordinateBounds`. `updateSavedPoints` parses GeoJSON FeatureCollection, creates `MLNShapeSource` + `MLNCircleStyleLayer` with dynamic color from feature properties (`circleColor = NSExpression(forKeyPath: "color")`), white stroke. Pending state for style reloads (same pattern as track line).
3. **Added `buildSavedPointsGeoJson`** — Builds GeoJSON FeatureCollection with Point features, each having `name` and `color` properties. Matches Android's `MapRenderUtils.updateSavedPointsOnMap()` format.
4. **Wired place search** — Debounced search via `snapshotFlow` + `debounce(300)` on `searchQuery`. Calls `PlaceSearchClient.search()` when query >= 2 chars. Result click animates camera to location at zoom 14.
5. **Wired viewing track** — Observes `trackRepository.viewingTrack`. Shows blue track line (`#0000FF`), fits camera to bounds via `setCameraBounds`. Close banner clears viewing track and track line. Recording (red) takes precedence over viewing (blue).
6. **Wired viewing point** — `viewingPoint` state lives in `IosApp` (matching Android pattern where it's in the navigator). "Show on map" from SavedPointsScreen sets `viewingPoint` and navigates to MAP. Camera centers on point at zoom 15. Close banner clears state.
7. **Wired saved points layer** — Observes `savedPointsRepository.savedPoints`. Renders as circle layer when `showSavedPoints` toggle is true. Toggle in layer menu controls visibility.
8. **Wired "show on map" for tracks** — From TracksScreen, calls `trackRepository.setViewingTrack(track)` and navigates to MAP.

### Not in scope (deferred)

- **Ruler tool** — Requires map tap handling (Swift bridge for tap coordinates → Kotlin callback), belongs in a separate phase.
- **Map tap/long-press handling** — Save point from map, point info on click.
- **Continue track** — Resume recording a saved track.
- **Online tracking toggle from map** — Toggle in layer menu is a no-op.

**Files modified:**
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/MapViewProvider.kt` — added 3 methods
- `iosApp/iosApp/MapViewFactory.swift` — implemented bounds fitting, saved points circle layer
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/TrackGeoJson.kt` — added `buildSavedPointsGeoJson`
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/IosMapScreen.kt` — wired search, viewing track/point, saved points
- `shared/src/iosMain/kotlin/no/synth/where/IosApp.kt` — viewingPoint state, "show on map" callbacks

**No changes to commonMain or Android** — fully backwards-compatible.

---

## Phase 13 — iOS sharing and file operations ✅ DONE

Wired up GPX import/export/save/open for tracks and online tracking sharing/view-on-web. All business logic was already in commonMain; this phase bridges Compose callbacks to iOS platform APIs.

### What was done

1. **Created `IosPlatformActions`** — Utility object in `shared/src/iosMain/kotlin/no/synth/where/util/IosPlatformActions.kt` with four functions:
   - `openUrl(url)` → `UIApplication.sharedApplication.openURL()`
   - `shareText(text)` → Present `UIActivityViewController` with text
   - `shareFile(fileName, content)` → Write to temp file via `NSData`, present `UIActivityViewController` with file URL
   - `pickFile(types, onResult)` → Present `UIDocumentPickerViewController` with UTType list, read file content via security-scoped access (`startAccessingSecurityScopedResource`/`stopAccessingSecurityScopedResource`)
   - Document picker delegate stored as class-level ref to prevent GC. `topViewController()` helper walks presented VC chain from key window root.

2. **Wired GPX import** — `onImport` calls `IosPlatformActions.pickFile` with `public.xml` and `org.topografix.gpx` UTTypes. Callback calls `trackRepository.importTrack(gpxContent)`. On null result or exception, sets `showImportError`/`importErrorMessage` state for the error dialog.

3. **Wired GPX export** — `onExport` uses `IosPlatformActions.shareFile` (share sheet with GPX file URL). File name sanitization matches Android (`replace(" ", "_").replace(":", "-")`). iOS only shows this single share button (Save and Open are hidden) since the iOS share sheet covers save-to-files, AirDrop, and open-in-app. `TracksScreenContent` accepts nullable `onSave`/`onOpen` — when null, those buttons are hidden and the layout adjusts.

4. **Wired online tracking** — Collects `trackingServerUrl` from `UserPreferences`. `onViewOnWeb` opens `${trackingServerUrl}?clients=$clientId` in Safari via `IosPlatformActions.openUrl`. `onShare` shares "Track my location: ..." text via `IosPlatformActions.shareText`.

### Key decisions

- **Single share button on iOS** — Android has three distinct actions (Save to Downloads, Open in external app, Share via intent). On iOS, the share sheet covers all three, so only the share/export button is shown. `TracksScreenContent` was updated to accept nullable `onSave`/`onOpen` callbacks — when null, buttons are hidden.
- **UTType for GPX** — Uses `org.topografix.gpx` (custom UTType identifier) and `public.xml` as fallback since not all file managers register the GPX type.
- **Security-scoped access** — Document picker requires `startAccessingSecurityScopedResource()`/`stopAccessingSecurityScopedResource()` to read files from other apps' sandboxes.

**Files created:**
- `shared/src/iosMain/kotlin/no/synth/where/util/IosPlatformActions.kt`

**Files modified:**
- `shared/src/iosMain/kotlin/no/synth/where/IosApp.kt` — wired all callbacks

**No changes to commonMain or Android** — fully backwards-compatible.

**Verification:**
- `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` — BUILD SUCCESSFUL
- `cd app && ../gradlew assembleDebug` — BUILD SUCCESSFUL (no Android regression)
- `cd app && ../gradlew testDebugUnitTest` — BUILD SUCCESSFUL (all tests pass)

---

## Phase 14 — iOS offline maps ✅ DONE

Wired offline map downloads on iOS, matching Android functionality. Extracted shared utilities from the Android implementation and created iOS-specific bridge to MapLibre's `MLNOfflineStorage`.

### What was done

1. **Extracted shared utilities to commonMain:**
   - `TileUtils.kt` — `estimateTileCount(bounds, minZoom, maxZoom)` pure math, moved from `MapDownloadManager`
   - `DownloadLayers.kt` — `DownloadLayers` object with tile URL mapping and style JSON generation, consolidating duplicated logic from `StyleServer` and `DownloadScreen`
   - `DownloadState.kt` — Shared `DownloadState` data class, moved from `MapDownloadService`'s inner class

2. **Updated Android to use shared utilities (backward-compatible):**
   - `MapDownloadManager` — delegates to `TileUtils.estimateTileCount`
   - `StyleServer` — uses `DownloadLayers.getDownloadStyleJson()` instead of inline tile URL mapping
   - `MapDownloadService` — imports shared `DownloadState` instead of inner class
   - `DownloadScreen` — uses `DownloadLayers.all` for layer display names

3. **Created iOS bridge interfaces** (`shared/src/iosMain`):
   - `OfflineMapManager` — callback-based interface for Swift implementation (avoids Kotlin lambda interop issues)
   - `OfflineMapDownloadObserver`, `OfflineMapRegionStatusCallback`, `OfflineMapDeleteCallback`, `OfflineMapLayerStatsCallback` — callback interfaces for clean Swift interop
   - `IosMapDownloadManager` — wraps `OfflineMapManager`, owns `StateFlow<DownloadState>`, converts callbacks to suspend functions via `suspendCoroutine`

4. **Created Swift implementation:**
   - `OfflineMapFactory.swift` — implements `OfflineMapManager` using `MLNOfflineStorage`
   - Downloads via `MLNTilePyramidOfflineRegion` + `addPack()`, observes `NSNotification.Name.MLNOfflinePackProgressChanged`
   - Metadata stored as JSON with `"name"` and `"layer"` keys (matches Android format)
   - Supports download, stop, get status, delete, and aggregate layer stats

5. **Wired iOS screens:**
   - Added `DOWNLOAD` and `LAYER_REGIONS` to `Screen` enum
   - `IosApp` accepts `offlineMapManager` parameter, creates `IosMapDownloadManager`, collects `downloadState`
   - Settings `onDownloadClick` navigates to download screen
   - `DownloadScreenContent` shows layers from `DownloadLayers.all` with descriptions
   - `LayerRegionsScreenContent` shows regions, wired to download/delete/stats

6. **Added shared tests:**
   - `TileUtilsTest` — returns at least 1, higher zoom → more tiles, larger region → more tiles
   - `DownloadLayersTest` — 6 unique layers, known URL, OSM fallback, style JSON structure
   - Added `commonTest` dependency on `kotlin("test")` in `shared/build.gradle.kts`

**Files created:**
- `shared/src/commonMain/.../data/TileUtils.kt`
- `shared/src/commonMain/.../data/DownloadLayers.kt`
- `shared/src/commonMain/.../data/DownloadState.kt`
- `shared/src/iosMain/.../data/OfflineMapManager.kt`
- `shared/src/iosMain/.../data/IosMapDownloadManager.kt`
- `iosApp/iosApp/OfflineMapFactory.swift`
- `shared/src/commonTest/.../data/TileUtilsTest.kt`
- `shared/src/commonTest/.../data/DownloadLayersTest.kt`

**Files modified:**
- `shared/src/androidMain/.../data/MapDownloadManager.kt` — use `TileUtils`
- `shared/src/androidMain/.../data/StyleServer.kt` — use `DownloadLayers`
- `app/.../service/MapDownloadService.kt` — import shared `DownloadState`
- `app/.../ui/DownloadScreen.kt` — use shared `DownloadState` + `DownloadLayers`
- `shared/src/iosMain/.../IosApp.kt` — download screens + `offlineMapManager` param
- `shared/src/iosMain/.../MainViewController.kt` — `offlineMapManager` param
- `iosApp/iosApp/ComposeView.swift` — create `OfflineMapFactory`
- `shared/build.gradle.kts` — `commonTest` dependency
- `KMP_MIGRATION.md`

**No changes to commonMain UI** — `DownloadScreenContent` and `LayerRegionsScreenContent` were already shared.

---

## Phase 15 — iOS hold-to-save-point and tap-to-edit-point

### What was done

1. **Extracted `SavedPointUtils` to commonMain:**
   - `findNearestPoint(tapLocation, savedPoints, maxDistanceMeters)` replaces inline proximity logic
   - Android `MapLibreMapView.kt` updated to use it

2. **Added gesture callback interfaces to `MapViewProvider`:**
   - `MapLongPressCallback` and `MapClickCallback` interfaces (matches `OfflineMapDownloadObserver` pattern for Swift interop)
   - `setOnLongPressCallback()` and `setOnMapClickCallback()` setters

3. **Implemented gestures in `MapViewFactory.swift`:**
   - `UILongPressGestureRecognizer` fires on `.began` state, converts screen point to map coordinate
   - `UITapGestureRecognizer` requires long press to fail first for correct priority
   - Both call Kotlin callbacks with lat/lng

4. **Wired save-point and edit-point in `IosMapScreen.kt`:**
   - Long press (when ruler inactive): opens `SavePointDialog` with auto-resolved name via `GeocodingHelper.reverseGeocode()` + `NamingUtils.makeUnique()`
   - Tap: uses `SavedPointUtils.findNearestPoint()` to find nearest saved point within 500m, opens `PointInfoDialog`
   - `PointInfoDialog` supports edit name/description/color, delete, save — matches Android behavior
   - Snackbar feedback on save/update/delete

5. **Added shared tests:**
   - `SavedPointUtilsTest` — empty list, within range, beyond max, closest of multiple, custom max distance

**Files created:**
- `shared/src/commonMain/.../data/SavedPointUtils.kt`
- `shared/src/commonTest/.../data/SavedPointUtilsTest.kt`

**Files modified:**
- `shared/src/iosMain/.../ui/map/MapViewProvider.kt` — callback interfaces + setters
- `iosApp/iosApp/MapViewFactory.swift` — gesture recognizers + callbacks
- `shared/src/iosMain/.../ui/map/IosMapScreen.kt` — save/edit point dialogs + gesture wiring
- `app/.../ui/map/MapLibreMapView.kt` — use `SavedPointUtils.findNearestPoint`
- `KMP_MIGRATION.md`

---

## Phase 16 — iOS ruler tool

### What was done

1. **Moved GeoJSON builders from iosMain to commonMain:**
   - `TrackGeoJson.kt` (iOS-only) → `MapGeoJson.kt` (shared)
   - Added `buildRulerLineGeoJson()` and `buildRulerPointsGeoJson()` for ruler overlay rendering
   - All four GeoJSON builders (`buildTrackGeoJson`, `buildSavedPointsGeoJson`, `buildRulerLineGeoJson`, `buildRulerPointsGeoJson`) now available to both platforms

2. **Added ruler rendering to `MapViewProvider` + `MapViewFactory.swift`:**
   - `updateRuler(lineGeoJson, pointsGeoJson)` and `clearRuler()` on the interface
   - Swift implementation: orange dashed line (#FFA500, dash pattern [2,2]) + orange circle points with white stroke — matching Android's `MapRenderUtils.updateRulerOnMap()`

3. **Wired full ruler functionality in `IosMapScreen.kt`:**
   - `rulerState` is now mutable (`var` with `mutableStateOf`)
   - Ruler toggle: activates/deactivates ruler mode (clears on deactivate)
   - Map tap when ruler active: adds ruler point
   - Undo: removes last point
   - Clear: resets ruler and clears map overlay
   - Save as Track: opens `SaveRulerAsTrackDialog` with auto-resolved name via reverse geocoding, saves via `TrackRepository.createTrackFromPoints()`

4. **Added shared tests:**
   - `RulerStateTest` — 10 tests: initial state, addPoint, removeLastPoint, clear, distances, segment distances, sum equality, active state preservation
   - `MapGeoJsonTest` — 8 tests: track/saved points/ruler line/ruler points GeoJSON structure, quote escaping, empty lists

**Files created:**
- `shared/src/commonMain/.../ui/map/MapGeoJson.kt`
- `shared/src/commonTest/.../data/RulerStateTest.kt`
- `shared/src/commonTest/.../ui/map/MapGeoJsonTest.kt`

**Files modified:**
- `shared/src/iosMain/.../ui/map/MapViewProvider.kt` — `updateRuler()` + `clearRuler()`
- `iosApp/iosApp/MapViewFactory.swift` — ruler line + point layers
- `shared/src/iosMain/.../ui/map/IosMapScreen.kt` — full ruler wiring + save-as-track dialog

**Files deleted:**
- `shared/src/iosMain/.../ui/map/TrackGeoJson.kt` — moved to commonMain as `MapGeoJson.kt`

---

## Phase 17 — iOS polish ✅ DONE

Made the iOS app production-ready: shared git-derived version info, app icon asset catalog, and TestFlight-ready Info.plist.

### What was done

1. **Shared `BuildInfo` object** — Added a Gradle task in `shared/build.gradle.kts` that generates `BuildInfo.kt` at build time with `GIT_COMMIT_COUNT`, `GIT_SHORT_SHA`, `BUILD_DATE`, and `VERSION_INFO`. Wired into `commonMain.kotlin.srcDir(...)` so both platforms use identical version info. Reuses the same `execGit()` pattern from `app/build.gradle.kts`.

2. **Updated version display** — iOS (`IosApp.kt`) replaced hardcoded `"Where iOS MVP"` with `BuildInfo.VERSION_INFO`. Android (`SettingsScreen.kt`) replaced `BuildConfig.GIT_COMMIT_COUNT/GIT_SHORT_SHA/BUILD_DATE` concatenation with `BuildInfo.VERSION_INFO`. Android `BuildConfig` fields kept for `versionCode`/`versionName`.

3. **App icon asset catalog** — Created `iosApp/iosApp/Assets.xcassets/` with `AppIcon.appiconset` (single 1024x1024 entry, modern Xcode generates all sizes). Added to Xcode project as `PBXFileReference` + `PBXBuildFile` in Resources phase.

4. **TestFlight-ready Info.plist** — Changed `CFBundleShortVersionString` to `$(MARKETING_VERSION)` and `CFBundleVersion` to `$(CURRENT_PROJECT_VERSION)`. These reference build settings from `Generated.xcconfig`.

5. **Version build script** — Extended the "Compile Kotlin Framework" shell script phase to generate `iosApp/Generated.xcconfig` with git-derived `CURRENT_PROJECT_VERSION` (commit count) and `MARKETING_VERSION` (1.0.{short-sha}). Both Debug and Release configurations reference this xcconfig. File is gitignored.

6. **BuildInfoTest** — Common test verifying `VERSION_INFO` is not blank, `GIT_COMMIT_COUNT` is numeric, `BUILD_DATE` matches yyyy-MM-dd.

**Files created:**
- `shared/src/commonTest/kotlin/no/synth/where/BuildInfoTest.kt`
- `iosApp/iosApp/Assets.xcassets/Contents.json`
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json`

**Files modified:**
- `shared/build.gradle.kts` — BuildInfo generation task + srcDir
- `shared/src/iosMain/.../IosApp.kt` — `BuildInfo.VERSION_INFO`
- `app/src/main/.../ui/SettingsScreen.kt` — `BuildInfo.VERSION_INFO`
- `iosApp/iosApp/Info.plist` — `$(MARKETING_VERSION)` / `$(CURRENT_PROJECT_VERSION)`
- `iosApp/iosApp.xcodeproj/project.pbxproj` — asset catalog + xcconfig + script
- `.gitignore` — `iosApp/iosApp/Generated.xcconfig`

## Phase 18 — iOS missing pieces ✅ DONE

Closed the three remaining feature gaps between iOS and Android: crash reporting, translations, and online tracking.

### What was done

1. **CrashReporter expect/actual** — Created `expect object CrashReporter` with `setEnabled(Boolean)` and `log(String)`. Android actual wraps `FirebaseCrashlytics.getInstance()`. iOS actual is a no-op that logs via `Logger`. Added `firebase-bom` + `firebase-crashlytics` to `shared/build.gradle.kts` androidMain dependencies. Updated `WhereApplication.kt`, `WhereApp.kt`, `IosApp.kt`, and `KoinHelper.kt` to use `CrashReporter.setEnabled()` instead of direct Crashlytics calls.

2. **Translations** — Added missing shared string resources (`recording_snackbar`, `track_discarded`, `track_saved`, `point_saved`, `point_deleted`, `point_updated`, `online_tracking_enabled`, `online_tracking_disabled`, `saved_as_track_name`, `location_permission_required`, `unnamed_point`, `import_gpx_corrupted`, `import_gpx_error`) with Norwegian translations. Replaced all hardcoded English strings in `IosMapScreen.kt` and `IosApp.kt` with `stringResource()` calls, hoisting at composable level for use in lambdas (same pattern as Android `MapScreen.kt`).

3. **Online tracking** — Moved `TRACKING_HMAC_SECRET` from `app/build.gradle.kts` `BuildConfig` to shared `BuildInfo` (generated at build time from env/local.properties). Updated `LocationTrackingService` to use `BuildInfo.TRACKING_HMAC_SECRET`. Added `onlineTrackingClient` property to `IosLocationTracker` — calls `sendPoint()` after each `addTrackPoint()`, mirroring Android's service. Wired full lifecycle in `IosMapScreen.kt`: creates `OnlineTrackingClient` on record start (when enabled), stops on discard/save, toggles mid-recording via `onOnlineTrackingChange`. Shows actual `onlineTrackingEnabled` state in the overlay.

4. **Tests** — Added `trackingHmacSecretIsNotBlank()` test to `BuildInfoTest.kt`. All Android tests, shared tests, and iOS framework link pass.

**Files created:**
- `shared/src/commonMain/kotlin/no/synth/where/util/CrashReporter.kt`
- `shared/src/androidMain/kotlin/no/synth/where/util/CrashReporter.kt`
- `shared/src/iosMain/kotlin/no/synth/where/util/CrashReporter.kt`

**Files modified:**
- `shared/build.gradle.kts` — Firebase deps in androidMain, HMAC secret in generateBuildInfo
- `shared/src/commonMain/composeResources/values/strings.xml` — snackbar + GPX error strings
- `shared/src/commonMain/composeResources/values-nb/strings.xml` — Norwegian translations
- `shared/src/commonTest/kotlin/no/synth/where/BuildInfoTest.kt` — HMAC test
- `shared/src/iosMain/kotlin/no/synth/where/IosApp.kt` — CrashReporter, translated GPX errors
- `shared/src/iosMain/kotlin/no/synth/where/ui/map/IosMapScreen.kt` — online tracking wiring, string resources
- `shared/src/iosMain/kotlin/no/synth/where/location/IosLocationTracker.kt` — onlineTrackingClient property
- `shared/src/iosMain/kotlin/no/synth/where/di/KoinHelper.kt` — CrashReporter init
- `app/src/main/java/no/synth/where/WhereApplication.kt` — CrashReporter
- `app/src/main/java/no/synth/where/WhereApp.kt` — CrashReporter
- `app/src/main/java/no/synth/where/service/LocationTrackingService.kt` — BuildInfo.TRACKING_HMAC_SECRET
- `app/build.gradle.kts` — removed TRACKING_HMAC_SECRET buildConfigField

## Phase 18 — iOS missing pieces, pt2

### Steps

1. Language selector
2. "Fortsett" on a track doesn't work
3. Overview over "Frakoblede kart" doesn't show the size of the downloaded maps
4. Should I worry over these XCode warnings?:
   * Run script build phase 'Copy Firebase Config' will be run during every build because it does not specify any outputs. To address this issue, either add output dependencies to the script phase, or configure it to run in every build by unchecking "Based on dependency analysis" in the script phase.
   * Run script build phase 'Compile Kotlin Framework' will be run during every build because the option to run the script phase "Based on dependency analysis" is unchecked.


---

## Known warnings

### Duplicate KLIB warnings (iOS metadata compilation)

`KLIB resolver: The same 'unique_name=...' found in more than one library` warnings appear during `:shared:compileIosMainKotlinMetadata`. This happens because AndroidX artifacts (`androidx.*`) and JetBrains Compose artifacts (`org.jetbrains.compose.*`, `org.jetbrains.androidx.*`) both bundle the same libraries at slightly different versions (e.g. `lifecycle-common` 2.9.4 vs 2.9.6, `runtime` 1.10.2 vs 1.10.1). The resolver picks one and it works fine. To fix, align dependency versions so both trees resolve to the same artifact. Harmless for now.

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
| `org.jetbrains.compose:compose-*` | Jetpack Compose (for shared UI) | Yes (added in Phase 8) |
| `androidx.room:room-*` | (keep) | Yes (since 2.7.0) |
| `androidx.datastore:datastore-*` | (keep) | Yes (since 1.1.0) |
| `org.maplibre.gl:android-sdk` | (keep, androidMain only) | No — platform-specific |

## Dependencies to remove

| Library | Replaced by |
|---|---|
| `com.squareup.okhttp3:okhttp` | Ktor (done in Phase 2) |
| `com.google.dagger:hilt-*` | Koin |
| `com.jakewharton.timber:timber` | Common Logger |
