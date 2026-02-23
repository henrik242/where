# Plan: Kartverket Vector Tile Layer

## Goal

Add a "Kartverket Vector" map layer that uses Kartverket's official vector tile service
(`vectortiles.kartverket.no`). This renders vector contour lines (10m intervals at zoom 14+),
elevation polygons, and detailed FKB data client-side via MapLibre, giving richer terrain
detail than the current raster WMTS tiles — comparable to what sporet.no shows.

The layer is online-only for now (no offline support, see below).

---

## Background

Current layers are all raster: `MapStyle.getStyle()` builds a GL style JSON with a single
raster tile source as the base, then injects the app's overlays (waymarked trails, county
borders, saved-point annotations) on top.

Vector tiles work differently: Kartverket publishes a **complete MapLibre GL style JSON** at
`https://vectortiles.kartverket.no/styles/v1/landtopo/style.json` that already contains all
sources, layers, glyphs, and sprites. We cannot construct this style ourselves — it has ~100
layers covering roads, buildings, water, contours, labels etc. Instead, we fetch it and inject
our overlay sources/layers into it.

This means `MapStyle.getStyle()` can no longer be a fully synchronous function for the vector
layer. The fetched style must be cached so subsequent calls are instant.

---

## Implementation steps

### 1. Fetch and cache the Kartverket vector style

Create `KartverketVectorStyle.kt` in `shared/src/commonMain/.../data/`:

```kotlin
object KartverketVectorStyle {
    private const val STYLE_URL =
        "https://vectortiles.kartverket.no/styles/v1/landtopo/style.json"

    private var cached: String? = null

    suspend fun fetch(httpClient: HttpClient): String {
        cached?.let { return it }
        val json = httpClient.get(STYLE_URL).bodyAsText()
        cached = json
        return json
    }

    fun getCached(): String? = cached
}
```

Pre-fetch the style in `MapScreenViewModel` (or equivalent) when the vector layer is first
selected, storing it in a `StateFlow<String?>`. The map view waits for the style to be ready
before rendering, same as it already does for the generated raster styles.

### 2. Add `KARTVERKET_VECTOR` to `MapLayer`

`shared/src/commonMain/.../ui/map/MapState.kt`:

```kotlin
enum class MapLayer {
    OSM, OPENTOPOMAP, KARTVERKET, TOPORASTER, SJOKARTRASTER,
    KARTVERKET_VECTOR   // new
}
```

### 3. Extend `MapStyle.getStyle()` to handle the vector base

`MapStyle.getStyle()` currently returns `String` synchronously. Keep that contract — but add
a new overload that accepts a pre-fetched style JSON string for the vector case:

```kotlin
fun getStyle(
    selectedLayer: MapLayer = MapLayer.KARTVERKET,
    showCountyBorders: Boolean,
    showWaymarkedTrails: Boolean,
    regions: List<Region>,
    vectorBaseStyle: String? = null   // non-null when selectedLayer == KARTVERKET_VECTOR
): String
```

When `selectedLayer == KARTVERKET_VECTOR`:
- Parse `vectorBaseStyle` as a JSON object
- Inject overlay sources into its `"sources"` map (waymarked trails raster, regions GeoJSON)
- Append overlay layers to its `"layers"` array (trails raster layer, region fill/outline/label)
- For glyphs: the Kartverket style already sets `"glyphs"` to their own font endpoint
  (`cache.kartverket.no/test/fonts/...`). Region labels use `"Noto Sans Regular"` in the
  current code but Kartverket only has `opensansregular`. Either:
  - Switch the injected label layers to `"opensansregular"`, or
  - Keep using the protomaps glyphs CDN for injected layers only (requires two glyph sources —
    not directly supported by MapLibre GL). **Simplest fix**: use `"opensansregular"` for
    injected annotation labels when the vector layer is active.
- Return the merged style JSON string

For all other layers, the function works exactly as today.

### 4. Add the layer to `MapFabColumn` and string resources

`MapFabColumn.kt` — add one `LayerMenuItem` entry under the Kartverket section:

```kotlin
LayerMenuItem(
    stringResource(Res.string.kartverket_vector),
    selectedLayer == MapLayer.KARTVERKET_VECTOR
) { onLayerSelected(MapLayer.KARTVERKET_VECTOR) }
```

`strings.xml`:
```xml
<string name="kartverket_vector">Kartverket (Vector)</string>
```

### 5. ViewModel wiring

In `MapScreenViewModel` (Android) and `IosMapScreen` (iOS), when `selectedLayer` changes to
`KARTVERKET_VECTOR`:
- Launch a coroutine to call `KartverketVectorStyle.fetch()`
- Expose the result as a `StateFlow<String?>` (null = loading)
- Pass it through to `MapStyle.getStyle()` as `vectorBaseStyle`
- The map UI should show a loading indicator (or just the previous map) while fetching

On subsequent selections of `KARTVERKET_VECTOR`, `getCached()` returns immediately.

---

## What to skip for now

**Offline support** — Vector tile offline packs are more complex than raster:
- The `landtopo` style references multiple tile sources (vector tiles + sprites + glyphs)
- MapLibre's offline manager needs to pack all of them, not just a single tile URL
- `DownloadLayers` would need a new abstraction for multi-source packs
- Leave `KARTVERKET_VECTOR` out of `DownloadLayers.all` for now; it will simply be
  unavailable in the offline download screen

**Custom styling** — The `landtopo` style is used as-is. Custom contour styling, hillshading,
or colour tweaks can be added later once the base integration works.

**Hillshading (3D terrain)** — Kartverket does not publish raster-dem tiles. MapLibre terrain
would require a third-party provider (e.g. MapTiler). Out of scope.

---

## Files changed

| File | Change |
|------|--------|
| `shared/.../data/KartverketVectorStyle.kt` | New — fetches + caches the style JSON |
| `shared/.../ui/map/MapState.kt` | Add `KARTVERKET_VECTOR` to enum |
| `shared/.../data/MapStyle.kt` | Handle vector base in `getStyle()` |
| `shared/.../ui/map/MapFabColumn.kt` | Add menu item |
| `shared/.../composeResources/values/strings.xml` | Add `kartverket_vector` string |
| `shared/.../composeResources/values-nb/strings.xml` | Norwegian translation |
| `app/.../MapScreenViewModel.kt` | Fetch vector style, pass to `MapStyle` |
| `shared/.../iosMain/.../IosMapScreen.kt` | Same for iOS |
