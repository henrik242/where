# Ski Trails Overlay — Feature Plan

## Status: Investigating data sources

Initial implementation using Geonorge WMS was stashed because the WMS endpoint (`wms.friluftsruter2`) appeared to have zero Oslo coverage. Further investigation revealed this was likely a wrong endpoint — the WFS at `wfs.turogfriluftsruter` actually has 309 ski trail features in the Oslo area.

## Data Sources

### 1. Waymarked Trails — Slopes (easiest, raster)

- **URL:** `https://tile.waymarkedtrails.org/slopes/{z}/{x}/{y}.png`
- **Format:** XYZ raster tiles, transparent PNG — same pattern as the existing hiking overlay
- **Data:** OpenStreetMap `piste:type=*` tags
- **Coverage:** Global, community-maintained. ~650 ski trail ways in the Skullerud/Grønmo/Sandbakken area alone
- **Pros:** Dead simple to add (one tile source), same pattern as existing hiking overlay, good OSM coverage in Oslo
- **Cons:** Raster only, no interactivity (can't tap a trail for info), no grooming status

### 2. Sporet / Skiforeningen (authoritative, ArcGIS raster)

- **URL:** `https://maps.sporet.no/arcgis/rest/services/Markadatabase_v2/Sporet_Simple/MapServer/export?bbox={bbox-epsg-3857}&bboxSR=3857&imageSR=3857&size=256,256&format=png&transparent=true&layers=show:6&f=image`
- **Format:** ArcGIS MapServer export (raster)
- **Data:** Skiforeningen's official trail database with classifications (lit, groomed, scooter, historical)
- **Coverage:** 32,000 km across ~800 ski areas in Norway
- **Pros:** Authoritative data from Skiforeningen, excellent Oslo/Marka coverage
- **Cons:** Third-party ArcGIS service (could change without notice), raster only, no grooming status

### 3. Løyper.net (vector tiles, grooming status)

- **Tile URL:** `https://api.loyper.net/segments/{z}/{x}/{y}` (MVT/protobuf)
- **TileJSON:** `https://api.loyper.net/segments`
- **Format:** Mapbox Vector Tiles served by Martin (PostGIS vector tile server)
- **Auth:** None — CORS enabled, unauthenticated
- **Data:** GPS-tracked grooming machine data from FMS Nordic AS. Segments are color-coded by time since last grooming
- **Coverage:** ~420 ski trail locations, mostly Norway (~340) plus Sweden (~60), Iceland, Switzerland, Germany, Latvia
- **Segment properties:** `id`, `location_id`, `track_id`, `is_active`, `last_update` (timestamp), `lighted` (bool), `open_not_groomed` (bool)
- **Additional layers:**
  - `https://api.loyper.net/locations/{z}/{x}/{y}` — ski area center points
  - `https://api.loyper.net/pois/{z}/{x}/{y}` — POIs (parking, cabins, etc.)
  - `https://api.loyper.net/segmentssnowmobile/{z}/{x}/{y}` — snowmobile trails
- **Grooming color scale:** 0-3h green → 3-6h → 6-18h → 18-48h → 2-7d → 7-15d → 15-30d → 30d+ grey. Closed = red dashed, lit = yellow dashed
- **Pros:** Vector tiles (native MapLibre support), real-time grooming status, rich metadata, no auth needed
- **Cons:** Third-party service (FMS Nordic), coverage depends on which ski areas use their GPS trackers

### 4. Skiappen.no / ViaTracks (REST API, grooming status)

- **API base:** `https://stage-api.viatracks.com/api`
- **Format:** Custom JSON (not GeoJSON/tiles). Each route network contains segments, each segment contains ordered coordinate points
- **Auth:** None for public endpoints
- **Data:** GPS-tracked grooming machines from Devinco AS (Trondheim). 816+ route networks, 244 active grooming machines
- **Key endpoints:**
  - `POST /Route/NetworksInArea` — trail segments in bounding box (body: `{MinLat, MaxLat, MinLon, MaxLon, IncludeSegments: true}`)
  - `GET /Route/Maintainers` — all active grooming machines with live GPS positions
  - `GET /Route/SearchNetworks?query=...` — search by name
- **Real-time:** SignalR WebSocket at `wss://viatracks-push.devinco.com/viatracksHub`
- **Grooming color scale:** <3h bright green → 3-12h green → 12-24h yellow → 24-48h orange → 2-7d blue → 7-15d purple → 15d+ grey
- **Pros:** Extensive coverage (816+ networks), real-time WebSocket updates, live machine positions
- **Cons:** Custom JSON format (needs conversion to GeoJSON for MapLibre), REST API (not tiles — must fetch/cache per viewport), proprietary service

### 5. Geonorge Turrutebasen WFS (vector, authoritative)

- **WFS endpoint:** `https://wfs.geonorge.no/skwms1/wfs.turogfriluftsruter`
- **Feature type:** `app:Skiløype` (11,240 total features nationwide)
- **Format:** WFS 2.0.0, GML 3.2.1 output only (no native GeoJSON)
- **Auth:** None — open data
- **Coverage:** **309 features in greater Oslo** (59.8-60.2N, 10.5-11.1E). Includes Nordmarka trails (Linderudkollen, TRY-løypa, Solemskogen-Revlia) and Østmarka trails (Lilloseter-Kjulsthern, Lysloypa Breisjon). Maintained by Oslo kommune Bymiljøetaten
- **Ski trail properties:** `merking` (marked/unmarked), `belysning` (lit), `preparering` (PM=machine/PS=snowmobile/U=user), `antallSkispor` (track count), `skøytetrase` (skating lane), `ryddebredde` (clearing width), `gradering` (difficulty G/B/R/S), `rutenavn`, `rutenummer`, `vedlikeholdsansvarlig`
- **Other route types:** `app:Fotrute` (137,824 hiking), `app:Sykkelrute` (11,225 cycling), `app:AnnenRute` (2,387 other), `app:RuteInfoPunkt` (9,470 POIs)
- **CRS:** EPSG:4258 (default), also supports 4326, 3857, 25832, 25833
- **Example query:** `?service=WFS&version=2.0.0&request=GetFeature&typeName=app:Skiløype&bbox=59.8,10.5,60.2,11.1&srsName=EPSG:4326&count=100`
- **Pros:** Authoritative government data, rich metadata (difficulty, lighting, grooming type, width), open data license, also has hiking/cycling routes
- **Cons:** GML only (needs parsing/conversion), not tiles (must fetch per viewport or pre-process), no grooming *status* (static trail definitions only), segments not full routes (group by `rutenummer`)

**Note:** The original WMS endpoint (`wms.friluftsruter2`) used in the stashed implementation may have been pointing to wrong/empty layers. The WFS at `wfs.turogfriluftsruter` has good Oslo coverage.

## Comparison

| Source | Format | Grooming status | Oslo coverage | Effort |
|---|---|---|---|---|
| Waymarked Trails | XYZ raster tiles | No | Good (OSM) | Trivial |
| Sporet | ArcGIS raster | No | Excellent | Low |
| Løyper.net | MVT vector tiles | Yes (real-time) | Depends on GPS adoption | Low-medium |
| Skiappen/ViaTracks | Custom JSON API | Yes (real-time) | Good (816+ networks) | Medium-high |
| Geonorge WFS | GML vector | No (static) | Good (309 features) | Medium |

## Recommendation

**Phase 1:** Add Waymarked Trails Slopes as the simplest ski trail overlay — identical pattern to existing hiking overlay, one line of tile config. Ship immediately.

**Phase 2:** Add Løyper.net vector tiles as a "grooming status" overlay. MVT tiles work natively with MapLibre, no auth needed, and the grooming timestamp enables color-coded freshness rendering. This gives users the most actionable information (which trails are freshly groomed).

**Phase 3 (optional):** Consider Geonorge WFS for static trail metadata (difficulty, lighting, width) or Skiappen for broader grooming coverage if Løyper.net doesn't cover enough areas.

## Implementation (stashed)

The stashed code follows the existing Waymarked Trails overlay pattern: a boolean toggle that conditionally adds a WMS/XYZ raster source + layer to the MapLibre style JSON.

### Files changed (in stash)

1. `shared/.../MapStyle.kt` — `showSkiTrails` parameter, raster source + layer
2. `app/.../MapLibreMapView.kt` — parameter passthrough
3. `app/.../MapScreenContent.kt` — parameter passthrough
4. `app/.../MapFabColumn.kt` — `LayerMenuItem` in overlays section
5. `app/.../MapScreen.kt` — `showSkiTrails` state + toggle
6. `app/.../strings.xml` — `ski_trails_turrutebasen` string resource
