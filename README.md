# Where?

Free offline hiking maps for Norway. A lightweight alternative to the discontinued [Hvor?](https://www.kartverket.no/til-lands/kart/hvor-appen) app from Kartverket, with topographic maps from Kartverket and OpenStreetMap.

## Features

- Free offline maps from Kartverket (topo, toporaster, nautical charts) and OpenStreetMap
- GPS tracking with background recording — track your hike with the screen locked
- Real-time location sharing — let friends and family follow your trip live
- Save and revisit tracks and points of interest
- GPX import/export — compatible with other hiking and navigation apps
- Distance measurement tool
- Map overlays: hiking trails from Waymarked Trails (OSM), county borders
- Lightweight and always free

## Building

### Android app

1. Copy `local.properties.example` to `local.properties` and fill in your values
2. Place your `google-services.json` in `app/` (from Firebase console)
3. `./gradlew assembleDebug`

### Web server

See [web/README.md](web/README.md).
