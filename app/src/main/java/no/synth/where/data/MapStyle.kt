package no.synth.where.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import no.synth.where.ui.MapLayer
import java.io.File

object MapStyle {
    private fun isOnline(context: Context): Boolean = try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        connectivityManager?.let {
            val activeNetwork = it.activeNetwork
            val capabilities = it.getNetworkCapabilities(activeNetwork)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } ?: true
    } catch (e: Exception) {
        true
    }

    private fun getTileUrls(
        context: Context,
        tilesDir: File,
        layerName: String,
        onlineUrl: String,
        coordPattern: String
    ): String {
        val offlinePath = "file://${File(tilesDir, layerName).absolutePath}"
        return if (isOnline(context)) {
            """["$onlineUrl"]"""
        } else {
            """["$offlinePath/$coordPattern.png"]"""
        }
    }

    fun getStyle(
        context: Context,
        selectedLayer: MapLayer = MapLayer.KARTVERKET,
        showCountyBorders: Boolean = true,
        showWaymarkedTrails: Boolean = false
    ): String {
        val regions = RegionsRepository.getRegions(context)
        val tilesDir = File(context.getExternalFilesDir(null), "tiles")
        val regionsGeoJson = regions.joinToString(",") { region ->
            val coordinates = if (region.polygon != null && region.polygon.isNotEmpty()) {
                region.polygon.first().joinToString(",") { latLng ->
                    "[${latLng.longitude}, ${latLng.latitude}]"
                }
            } else {
                val b = region.boundingBox
                val north = b.northEast.latitude
                val south = b.southWest.latitude
                val east = b.northEast.longitude
                val west = b.southWest.longitude
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

        val osmOpacity = if (selectedLayer == MapLayer.OSM) 1.0 else 0.01
        val kartverketOpacity = if (selectedLayer == MapLayer.KARTVERKET) 1.0 else 0.01
        val toporasterOpacity = if (selectedLayer == MapLayer.TOPORASTER) 1.0 else 0.01
        val sjokartrasterOpacity = if (selectedLayer == MapLayer.SJOKARTRASTER) 1.0 else 0.01
        val openTopoMapOpacity = if (selectedLayer == MapLayer.OPENTOPOMAP) 1.0 else 0.01
        val waymarkedTrailsOpacity = if (showWaymarkedTrails) 1.0 else 0.0

        val countyBordersLayers = if (showCountyBorders) """
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
""" else ""

        return """
{
  "version": 8,
  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
  "sources": {
    "osm": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "osm",
                "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                "{z}/{x}/{y}"
            )
        },
      "tileSize": 256,
      "attribution": "© OpenStreetMap contributors"
    },
    "kartverket": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "kartverket",
                "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png",
                "{z}/{y}/{x}"
            )
        },
      "tileSize": 256,
      "attribution": "Kartverket"
    },
    "toporaster": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "toporaster",
                "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png",
                "{z}/{y}/{x}"
            )
        },
      "tileSize": 256,
      "attribution": "Kartverket Toporaster"
    },
    "sjokartraster": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "sjokartraster",
                "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png",
                "{z}/{y}/{x}"
            )
        },
      "tileSize": 256,
      "attribution": "Kartverket Sjøkartraster"
    },
    "opentopomap": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "opentopomap",
                "https://tile.opentopomap.org/{z}/{x}/{y}.png",
                "{z}/{x}/{y}"
            )
        },
      "tileSize": 256,
      "attribution": "© OpenTopoMap (CC-BY-SA)"
    },
    "waymarkedtrails": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ${
            getTileUrls(
                context,
                tilesDir,
                "waymarkedtrails",
                "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png",
                "{z}/{x}/{y}"
            )
        },
      "tileSize": 256,
      "attribution": "© Waymarked Trails, OSM"
    },
    "regions": {
      "type": "geojson",
      "data": {
        "type": "FeatureCollection",
        "features": [$regionsGeoJson]
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
      "id": "toporaster-layer",
      "type": "raster",
      "source": "toporaster",
      "paint": {
        "raster-opacity": $toporasterOpacity
      }
    },
    {
      "id": "sjokartraster-layer",
      "type": "raster",
      "source": "sjokartraster",
      "paint": {
        "raster-opacity": $sjokartrasterOpacity
      }
    },
    {
      "id": "opentopomap-layer",
      "type": "raster",
      "source": "opentopomap",
      "paint": {
        "raster-opacity": $openTopoMapOpacity
      }
    },
    {
      "id": "waymarkedtrails-layer",
      "type": "raster",
      "source": "waymarkedtrails",
      "paint": {
        "raster-opacity": $waymarkedTrailsOpacity
      }
    }${if (showCountyBorders) "," else ""}
    $countyBordersLayers
  ]
}
"""
    }
}

