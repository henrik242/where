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

## Phase 11 — iOS location and track recording (planned)

### Steps
1. Add `NSLocationAlwaysAndWhenInUseUsageDescription` to Info.plist
2. Add background mode `location` in Xcode capabilities
3. Create `IosLocationManager` in iosMain using `CLLocationManager`
4. Wire to `TrackRepository.addTrackPoint()` on location updates
5. Connect record/stop FAB in `IosMapScreen`
6. Render current track as MapLibre line layer via Swift bridge

---

## Phase 12 — iOS map interaction (planned)

### Steps
1. Extend `MapViewProvider` with tap handling, camera change callbacks
2. Implement place search (wire `PlaceSearchClient` to search overlay)
3. Implement ruler tool (tap-to-add points on map)
4. Render saved points as MapLibre symbol layer
5. Render tracks as MapLibre line layers (viewing track + recording track)
6. Implement "show on map" for tracks and saved points (camera animation)

---

## Phase 13 — iOS sharing and file operations (planned)

### Steps
1. GPX export via `UIActivityViewController`
2. GPX import via `UIDocumentPickerViewController`
3. Online tracking link sharing via `UIActivityViewController`
4. Open tracking URL in Safari via `UIApplication.shared.open`

---

## Phase 14 — iOS offline maps (planned)

### Steps
1. Implement `MapDownloadManager` equivalent using MapLibre iOS `MLNOfflineStorage`
2. Wire `DownloadScreenContent` to real download/delete/progress operations
3. Region-based tile caching matching the Android download regions

---

## Phase 15 — iOS polish (planned)

### Steps
1. Implement `ZipUtils` for county borders (Foundation compression or third-party zip)
2. Git-derived version info (build script or Xcode build phase)
3. App icon and launch screen
4. TestFlight distribution setup

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
