package no.synth.where.data

import no.synth.where.ui.map.MapLayer

object MapStyle {
    /** Network fallback used only by tests; production callers pass a platform-local URL. */
    private const val DEFAULT_GLYPHS_URL =
        "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf"

    fun getStyle(
        selectedLayer: MapLayer = MapLayer.KARTVERKET,
        showWaymarkedTrails: Boolean = false,
        showAvalancheZones: Boolean = false,
        showHillshade: Boolean = false,
        glyphsUrl: String = DEFAULT_GLYPHS_URL,
    ): String {
        data class TileSource(val id: String, val tiles: String, val attribution: String)

        val baseSource = when (selectedLayer) {
            MapLayer.OSM -> TileSource("osm", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap</a> contributors")
            MapLayer.KARTVERKET -> TileSource("kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            MapLayer.TOPORASTER -> TileSource("toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            MapLayer.SJOKARTRASTER -> TileSource("sjokartraster", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png", "© <a href='https://www.kartverket.no'>Kartverket</a>")
            MapLayer.OPENTOPOMAP -> TileSource("opentopomap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© <a href='https://opentopomap.org'>OpenTopoMap</a> (CC-BY-SA)")
            MapLayer.MAPANT -> TileSource("mapant", "https://mapant.no/tiles/osm/{z}/{x}/{y}.png", "© <a href='https://mapant.no'>MapAnt.no</a>")
        }

        val sources = buildString {
            append("""
    "${baseSource.id}": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["${baseSource.tiles}"],
      "tileSize": 256,
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
            if (showAvalancheZones) {
                append(""",
    "avalanchezones": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["https://gis3.nve.no/arcgis/rest/services/wmts/Bratthet_med_utlop_2024/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "attribution": "© <a href='https://www.nve.no'>NVE</a> (NLOD)",
      "minzoom": 6,
      "maxzoom": 19
    }""")
            }
            if (showHillshade) {
                append(""",
    "hillshade": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["https://s3.amazonaws.com/elevation-tiles-prod/normal/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 15,
      "attribution": "© <a href='https://github.com/tilezen/joerd'>Tilezen Joerd</a> (data from USGS, GMTED, SRTM, ETOPO1)"
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
            if (showHillshade) {
                append(""",
    {
      "id": "hillshade-layer",
      "type": "raster",
      "source": "hillshade",
      "paint": {
        "raster-opacity": 0.3
      }
    }""")
            }
            if (showAvalancheZones) {
                append(""",
    {
      "id": "avalanchezones-layer",
      "type": "raster",
      "source": "avalanchezones",
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

