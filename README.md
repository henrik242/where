# Where?

Norwegian hiking maps for Android with real-time online tracking and offline map support.

## Features

- Live GPS tracking with background recording
- Offline map tile downloads (MapLibre)
- GPX import/export
- Real-time track sharing via WebSocket server
- Saved points, distance measurement, county borders

## Building

### Android app

1. Copy `local.properties.example` to `local.properties` and fill in your values
2. Place your `google-services.json` in `app/` (from Firebase console)
3. `./gradlew assembleDebug`

### Web server

See [web/README.md](web/README.md).
