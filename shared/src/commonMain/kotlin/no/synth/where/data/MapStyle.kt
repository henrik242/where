package no.synth.where.data

import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.NveOverlay

object MapStyle {
    /** Network fallback used only by tests; production callers pass a platform-local URL. */
    private const val DEFAULT_GLYPHS_URL =
        "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf"

    /** OpenMapTiles-schema vector tiles, no API key. TileJSON, so the weekly planet build stays current. */
    const val OSM_PATHS_TILEJSON_URL = "https://tiles.openfreemap.org/planet"

    /**
     * Lowest zoom with any paths at all; z11 and below carry none. OpenMapTiles thins them on the
     * way down -- z12 keeps rank-1 routes, z13 adds named and sac_scale ones, z14 has everything --
     * so zoomed-out views show a subset. Public so the screens can prompt for a zoom-in below this.
     */
    const val OSM_PATHS_MIN_ZOOM = 12

    /** Zoom from which the source carries every path, not just the ones it keeps when thinning. */
    const val OSM_PATHS_COMPLETE_ZOOM = 14

    /** class=path also covers station platforms and indoor corridors in OpenMapTiles; drop those. */
    private const val OSM_PATHS_FILTER =
        """["all", ["in", "class", "path", "track"], ["!in", "subclass", "platform", "corridor"]]"""

    fun getStyle(
        selectedLayer: MapLayer = MapLayer.KARTVERKET,
        showWaymarkedTrails: Boolean = false,
        nveOverlay: NveOverlay? = null,
        showOsmPaths: Boolean = false,
        glyphsUrl: String = DEFAULT_GLYPHS_URL,
    ): String {
        data class TileSource(val id: String, val tiles: String, val attribution: String, val maxZoom: Int? = null)

        val baseSource = when (selectedLayer) {
            MapLayer.OSM -> TileSource("osm", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors")
            MapLayer.KARTVERKET -> TileSource("kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            MapLayer.TOPORASTER -> TileSource("toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            MapLayer.SJOKARTRASTER -> TileSource("sjokartraster", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            // OpenTopoMap serves tiles only through z17 (z18+ returns 404), so cap here and let
            // MapLibre overzoom locally instead of fetching missing tiles.
            MapLayer.OPENTOPOMAP -> TileSource("opentopomap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© <a href='https://opentopomap.org'>OpenTopoMap</a> (CC-BY-SA)", maxZoom = 17)
            // MapAnt serves tiles only through z16 (z17+ returns 404), so cap here and let
            // MapLibre overzoom locally instead of fetching missing tiles.
            MapLayer.MAPANT -> TileSource("mapant", "https://mapant.no/tiles/osm/{z}/{x}/{y}.png", "© <a href='https://mapant.no'>MapAnt.no</a>", maxZoom = 16)
            // EOX Sentinel-2 cloudless annual mosaic (bump the year in the URL yearly).
            // Capped at z14: native ~10 m resolution has no detail past z14, so MapLibre
            // overzooms locally instead of fetching blurry upscaled tiles.
            MapLayer.SATELLITE -> TileSource("satellite", "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2025_3857/default/g/{z}/{y}/{x}.jpg", "EOxCloudless <a href='https://cloudless.eox.at'>cloudless.eox.at</a> by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2025)", maxZoom = 14)
        }

        val maxZoomLine = baseSource.maxZoom?.let { "\n      \"maxzoom\": $it," }.orEmpty()

        val sources = buildString {
            append("""
    "${baseSource.id}": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["${baseSource.tiles}"],
      "tileSize": 256,$maxZoomLine
      "attribution": "${baseSource.attribution}"
    }""")
            if (showWaymarkedTrails) {
                append(""",
    "waymarkedtrails": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "attribution": "© <a href='https://waymarkedtrails.org'>Waymarked Trails</a> (CC-BY-SA)"
    }""")
            }
            if (nveOverlay != null) {
                append(""",
    "${nveOverlay.sourceId}": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["${nveOverlay.tileUrl}"],
      "tileSize": 256,
      "attribution": "© <a href='https://www.nve.no'>NVE</a> (NLOD)",
      "minzoom": ${NveOverlay.MIN_ZOOM},
      "maxzoom": ${NveOverlay.MAX_ZOOM}
    }""")
            }
            if (showOsmPaths) {
                append(""",
    "osmpaths": {
      "type": "vector",
      "url": "$OSM_PATHS_TILEJSON_URL",
      "attribution": "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors, tiles <a href='https://openfreemap.org'>OpenFreeMap</a> © <a href='https://www.openmaptiles.org/'>OpenMapTiles</a>"
    }""")
            }
        }

        val layers = buildString {
            append("""
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#f0f0f0"
      }
    },
    {
      "id": "base-layer",
      "type": "raster",
      "source": "${baseSource.id}",
      "paint": {
        "raster-opacity": 1.0
      }
    }""")
            if (nveOverlay != null) {
                append(""",
    {
      "id": "${nveOverlay.sourceId}-layer",
      "type": "raster",
      "source": "${nveOverlay.sourceId}",
      "paint": {
        "raster-opacity": 0.6
      }
    }""")
            }
            if (showWaymarkedTrails) {
                append(""",
    {
      "id": "waymarkedtrails-layer",
      "type": "raster",
      "source": "waymarkedtrails",
      "paint": {
        "raster-opacity": 1.0
      }
    }""")
            }
            if (showOsmPaths) {
                // Yellow casing under a black dash, like MapAnt's own OSM path overlay. Vector tiles,
                // so the dashes stay sharp when overzoomed past the source's z14. The casing carries
                // the layer on dark ground (satellite), where the dash alone disappears.
                append(""",
    {
      "id": "osmpaths-casing",
      "type": "line",
      "source": "osmpaths",
      "source-layer": "transportation",
      "filter": $OSM_PATHS_FILTER,
      "minzoom": $OSM_PATHS_MIN_ZOOM,
      "layout": {
        "line-cap": "round",
        "line-join": "round"
      },
      "paint": {
        "line-color": "#ffff00",
        "line-opacity": 0.8,
        "line-width": ["interpolate", ["linear"], ["zoom"], $OSM_PATHS_MIN_ZOOM, 3, $OSM_PATHS_COMPLETE_ZOOM, 4, 16, 7, 20, 11]
      }
    },
    {
      "id": "osmpaths-line",
      "type": "line",
      "source": "osmpaths",
      "source-layer": "transportation",
      "filter": $OSM_PATHS_FILTER,
      "minzoom": $OSM_PATHS_MIN_ZOOM,
      "layout": {
        "line-cap": "butt",
        "line-join": "round"
      },
      "paint": {
        "line-color": "#000000",
        "line-width": ["interpolate", ["linear"], ["zoom"], $OSM_PATHS_MIN_ZOOM, 1.1, $OSM_PATHS_COMPLETE_ZOOM, 1.4, 16, 1.9, 20, 2.6],
        "line-dasharray": [2.5, 1.5]
      }
    }""")
            }
        }

        return """
{
  "version": 8,
  "glyphs": "$glyphsUrl",
  "sources": {$sources
  },
  "layers": [$layers
  ]
}
"""
    }
}

