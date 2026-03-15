# Where?

Free offline hiking maps for Norway. A lightweight alternative to the discontinued [Hvor?](https://www.kartverket.no/til-lands/kart/hvor-appen) app from Kartverket, with topographic maps from Kartverket and OpenStreetMap.

<a href="https://apps.apple.com/app/where/id6760362061">
  <img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" alt="Download on the App Store" width="151">
</a>
<a href="https://play.google.com/store/apps/details?id=no.synth.where">
  <img src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" alt="Get it on Google Play" width="170">
</a>

## Features

- Free offline maps from Kartverket (topo, toporaster, nautical charts), MapAnt (LiDAR-based orienteering maps), and OpenStreetMap
- GPS tracking with background recording — track your hike with the screen locked
- Real-time location sharing — let friends and family follow your trip live
- Save and revisit tracks and points of interest
- GPX import/export — compatible with other hiking and navigation apps
- Distance measurement tool
- Map overlays: hiking trails (Waymarked Trails), avalanche runout zones (NVE), county borders
- Lightweight and always free

## Building

### Android app

1. Copy `local.properties.example` to `local.properties` and fill in your values
2. Place your `google-services.json` in `app/` (from Firebase console)
3. `./gradlew assembleDebug`

### iOS app

1. Set up `local.properties` as above (needed for the shared module build)
2. Build the shared framework: `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64`
3. Open `iosApp/Where.xcodeproj` in Xcode
4. Xcode will fetch MapLibre via SPM on first open — wait for package resolution to finish
5. Select an iPhone simulator and press Run (Cmd+R)

The Gradle build phase in Xcode (`embedAndSignAppleFrameworkForXcode`) compiles the shared Kotlin framework automatically, so after the initial setup you can iterate from Xcode directly.

To verify just the shared framework without Xcode:
```
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64   # simulator
./gradlew :shared:linkDebugFrameworkIosArm64            # device
```

### Web server

See [web/README.md](web/README.md).
