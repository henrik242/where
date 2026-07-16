# Where?

Free, lightweight offline hiking maps for Norway. A spiritual successor to the discontinued [Hvor?](https://www.kartverket.no/til-lands/kart/hvor-appen) app from Kartverket, with topographic maps from Kartverket, OpenStreetMap, and more. Available for Android and iOS, and always free. See [RELEASES.md](RELEASES.md) for what's new in each release.

<a href="https://apps.apple.com/app/where/id6760362061"><img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" alt="Download on the App Store" width="151"></a>
<a href="https://play.google.com/store/apps/details?id=no.synth.where"><img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" width="170"></a>
<a href="https://buymeacoffee.com/henrik242"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="170"></a>

## Features

### Maps

- Offline base maps: Kartverket (topo, toporaster, nautical charts), MapAnt (LiDAR-based orienteering maps), OpenTopoMap, OpenStreetMap, and Sentinel-2 satellite imagery
- Overlays: waymarked hiking trails, avalanche terrain from NVE (slope steepness and runout zones), hillshade, a coordinate grid, and a crosshair with live elevation and slope readout

### Location and sharing

- GPS recording that keeps running with the screen locked
- Real-time location sharing so friends and family can follow your trip live
- Follow a friend's live track on your own map

### Tracks

- Save, revisit, import (GPX and FIT), and export (GPX) tracks, compatible with other hiking and navigation apps
- Organize saved tracks into folders, with move, rename, and remove actions and undo
- Navigate along a saved track with remaining distance, elevation, an off-course arrow pointing back to the route, and a reverse-direction toggle
- Show several tracks at once, each in its own color, and tap one to focus it
- Elevation chart you can scrub to mark the matching point on the map
- Crop the start and end of a saved track

### Tools

- Place search
- Distance measurement with the ruler or a two-finger gesture
- Save and manage points of interest
- Coordinate readout in UTM, MGRS, latitude/longitude, or DMS

## Built with

Kotlin Multiplatform sharing navigation, geometry, storage, and Compose Multiplatform UI between Android and iOS. Maps render with MapLibre, and tracks and points are stored locally with Room. The optional live-tracking backend under [`web/`](web/) runs on Bun.

## Building

### Android app

Requires Android 13 (API 33) or newer.

1. Copy `local.properties.example` to `local.properties` and fill in your values
2. Place your `google-services.json` in `app/` (from the Firebase console)
3. `./gradlew assembleDebug`

### iOS app

1. Set up `local.properties` as above (the shared module build needs it)
2. Build the shared framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
3. Open `iosApp/Where.xcodeproj` in Xcode
4. Wait for Xcode to fetch MapLibre via SPM on first open
5. Select an iPhone simulator and press Run (Cmd+R)

The Gradle build phase in Xcode (`embedAndSignAppleFrameworkForXcode`) compiles the shared Kotlin framework automatically, so after the initial setup you can iterate from Xcode directly.

To verify just the shared framework without Xcode:

```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # simulator
./gradlew :shared:linkDebugFrameworkIosArm64            # device
```

### Web server

See [web/README.md](web/README.md).

## License

[Mozilla Public License 2.0](LICENSE).
