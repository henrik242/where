package no.synth.where.data

import android.content.Context
import java.io.File

object MapStyle {
    fun getStyle(context: Context, useKartverket: Boolean = true): String {
        val regions = RegionsRepository.getRegions(context)
        val regionsGeoJson = regions.joinToString(",") { region ->
            // Use actual polygon if available, otherwise fall back to bounding box
            val coordinates = if (region.polygon != null && region.polygon.isNotEmpty()) {
                // Use actual fylke polygon boundaries
                region.polygon.first().joinToString(",") { latLng ->
                    "[${latLng.longitude}, ${latLng.latitude}]"
                }
            } else {
                // Fallback to bounding box rectangle
                val b = region.boundingBox
                val north = b.northEast.latitude
                val south = b.southWest.latitude
                val east = b.northEast.longitude
                val west = b.southWest.longitude
                "[$west, $north],[$west, $south],[$east, $south],[$east, $north],[$west, $north]"
            }

            """
            {
              "type": "Feature",
              "properties": { "name": "${region.name}" },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[$coordinates]]
              }
            }
            """
        }

        // Check if we have local tiles downloaded
        val tilesDir = File(context.getExternalFilesDir(null), "tiles/kartverket")
        val hasLocalTiles = tilesDir.exists() && tilesDir.listFiles()?.isNotEmpty() == true

        // Use online Kartverket tiles (corrected URL format with {z}/{y}/{x})
        val kartverketTilesUrl = if (false && hasLocalTiles) {
            // MapLibre on Android needs file:// protocol
            "file://${tilesDir.absolutePath}/{z}/{y}/{x}.png"
        } else {
            // Kartverket uses {z}/{y}/{x} format, not {z}/{x}/{y}!
            "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"
        }

        // Restore proper layer switching
        val kartverketOpacity = if (useKartverket) 1.0 else 0.01
        val osmOpacity = if (useKartverket) 0.01 else 1.0



        return """
{
  "version": 8,
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "osm": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": [
        "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
      ],
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    },
    "kartverket": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": [
        "$kartverketTilesUrl"
      ],
      "tileSize": 256,
      "attribution": "Kartverket"
    },
    "regions": {
      "type": "geojson",
      "data": {
        "type": "FeatureCollection",
        "features": [
          $regionsGeoJson
        ]
      }
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#f0f0f0"
      }
    },
    {
      "id": "osm-layer",
      "type": "raster",
      "source": "osm",
      "paint": {
        "raster-opacity": $osmOpacity
      }
    },
    {
      "id": "kartverket-layer",
      "type": "raster",
      "source": "kartverket",
      "paint": {
        "raster-opacity": $kartverketOpacity
      }
    },
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
    }
  ]
}
"""
    }
}

