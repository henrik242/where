package no.synth.where.data

object MapStyle {
    fun getStyle(): String {
        val regionsGeoJson = RegionsRepository.regions.joinToString(",") { region ->
            val b = region.boundingBox
            val north = b.northEast.latitude
            val south = b.southWest.latitude
            val east = b.northEast.longitude
            val west = b.southWest.longitude
            """
            {
              "type": "Feature",
              "properties": { "name": "${region.name}" },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[
                  [$west, $north],
                  [$west, $south],
                  [$east, $south],
                  [$east, $north],
                  [$west, $north]
                ]]
              }
            }
            """
        }

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
        "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{x}/{y}.png"
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
        "raster-opacity": 0.3
      }
    },
    {
      "id": "kartverket-layer",
      "type": "raster",
      "source": "kartverket",
      "paint": {}
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

