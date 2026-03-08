package no.synth.where.data

import no.synth.where.ui.map.MapLayer

object MapStyle {
    fun getStyle(
        selectedLayer: MapLayer = MapLayer.KARTVERKET,
        showCountyBorders: Boolean = true,
        showWaymarkedTrails: Boolean = false,
        showAvalancheZones: Boolean = false,
        regions: List<Region> = emptyList()
    ): String {
        val activeRegions = if (showCountyBorders) regions else emptyList()
        val regionsGeoJson = activeRegions.joinToString(",") { region ->
            val coordinates = if (region.polygon != null && region.polygon.isNotEmpty()) {
                region.polygon.first().joinToString(",") { latLng ->
                    "[${latLng.longitude}, ${latLng.latitude}]"
                }
            } else {
                val b = region.boundingBox
                val north = b.north
                val south = b.south
                val east = b.east
                val west = b.west
                "[$west, $north],[$west, $south],[$east, $south],[$east, $north],[$west, $north]"
            }

            val cleanName = region.name.substringBefore(" - ")

            """
            {
              "type": "Feature",
              "properties": { "name": "$cleanName" },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[$coordinates]]
              }
            }
            """
        }

        data class TileSource(val id: String, val tiles: String, val attribution: String)

        val baseSource = when (selectedLayer) {
            MapLayer.OSM -> TileSource("osm", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors")
            MapLayer.KARTVERKET -> TileSource("kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png", "Kartverket")
            MapLayer.TOPORASTER -> TileSource("toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png", "Kartverket Toporaster")
            MapLayer.SJOKARTRASTER -> TileSource("sjokartraster", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png", "Kartverket Sjøkartraster")
            MapLayer.OPENTOPOMAP -> TileSource("opentopomap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", "© OpenTopoMap (CC-BY-SA)")
            MapLayer.MAPANT -> TileSource("mapant", "https://mapant.no/tiles/osm/{z}/{x}/{y}.png", "© MapAnt.no")
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
      "attribution": "© Waymarked Trails, OSM"
    }""")
            }
            if (showAvalancheZones) {
                append(""",
    "avalanchezones": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["https://gis3.nve.no/arcgis/rest/services/wmts/Bratthet_med_utlop_2024/MapServer/tile/{z}/{y}/{x}"],
      "tileSize": 256,
      "attribution": "NVE",
      "minzoom": 6,
      "maxzoom": 19
    }""")
            }
            append(""",
    "regions": {
      "type": "geojson",
      "data": {
        "type": "FeatureCollection",
        "features": [$regionsGeoJson]
      }
    }""")
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
            if (showCountyBorders) {
                append(""",
    {
      "id": "regions-fill",
      "type": "fill",
      "source": "regions",
      "paint": {
        "fill-color": "#FF8800",
        "fill-opacity": 0.0
      }
    },
    {
      "id": "regions-outline",
      "type": "line",
      "source": "regions",
      "paint": {
        "line-color": "#ff0000",
        "line-width": 2
      }
    },
    {
      "id": "regions-label",
      "type": "symbol",
      "source": "regions",
      "layout": {
        "text-field": ["get", "name"],
        "text-font": ["Noto Sans Regular"],
        "text-size": 14,
        "text-anchor": "center"
      },
      "paint": {
        "text-color": "#333333",
        "text-halo-color": "#ffffff",
        "text-halo-width": 2
      }
    }""")
            }
        }

        return """
{
  "version": 8,
  "glyphs": "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf",
  "sources": {$sources
  },
  "layers": [$layers
  ]
}
"""
    }
}

