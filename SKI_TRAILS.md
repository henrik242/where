# Ski Trails Overlay — Feature Plan

## Status: Stashed

Initial implementation using Geonorge Turrutebasen was completed but stashed because the data source has very poor coverage in the Oslo area (Østmarka, Nordmarka).

## Problem

Turrutebasen relies on voluntary data submission. Skiforeningen, which manages trails in Oslomarka, does not contribute to it. The dataset is mostly populated in northern Norway.

## Data Sources

### 1. Waymarked Trails — Slopes (recommended, easiest)

- **URL:** `https://tile.waymarkedtrails.org/slopes/{z}/{x}/{y}.png`
- **Format:** Standard XYZ raster tiles, transparent PNG — same pattern as the existing hiking overlay
- **Data:** OpenStreetMap `piste:type=*` tags. ~650 ski trail ways mapped in the Skullerud/Grønmo/Sandbakken area alone
- **Coverage:** Global, community-maintained

### 2. Sporet / Skiforeningen (authoritative, ArcGIS)

- **URL:** `https://maps.sporet.no/arcgis/rest/services/Markadatabase_v2/Sporet_Simple/MapServer/export?bbox={bbox-epsg-3857}&bboxSR=3857&imageSR=3857&size=256,256&format=png&transparent=true&layers=show:6&f=image`
- **Data:** Skiforeningen's official trail database with classifications (lit, groomed, scooter, historical)
- **Coverage:** 32,000 km across ~800 ski areas in Norway
- **Risk:** Third-party ArcGIS service, could change without notice

### 3. Geonorge Turrutebasen (original, poor Oslo coverage)

- **WMS:** `https://wms.geonorge.no/skwms1/wms.friluftsruter2?service=WMS&version=1.3.0&request=GetMap&layers=Skiloype&styles=&crs=EPSG:3857&bbox={bbox-epsg-3857}&width=256&height=256&format=image/png&transparent=true`
- **Coverage:** Mostly northern Norway. Zero features in Østmarka/Nordmarka areas.

## Implementation

The stashed code follows the existing Waymarked Trails overlay pattern: a boolean toggle that conditionally adds a WMS/XYZ raster source + layer to the MapLibre style JSON.

### Files changed (in stash)

1. `shared/.../MapStyle.kt` — `showSkiTrails` parameter, raster source + layer
2. `app/.../MapLibreMapView.kt` — parameter passthrough
3. `app/.../MapScreenContent.kt` — parameter passthrough
4. `app/.../MapFabColumn.kt` — `LayerMenuItem` in overlays section
5. `app/.../MapScreen.kt` — `showSkiTrails` state + toggle
6. `app/.../strings.xml` — `ski_trails_turrutebasen` string resource

### Recommendation

Replace the Turrutebasen source with Waymarked Trails Slopes (option 1). Consider adding Sporet (option 2) as a second overlay for users who want Skiforeningen's official data.
