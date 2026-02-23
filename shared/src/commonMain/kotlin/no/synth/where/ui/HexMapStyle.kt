package no.synth.where.ui

import no.synth.where.data.DownloadLayers
import no.synth.where.data.HexGrid

const val EMPTY_HEX_GEOJSON = """{"type":"FeatureCollection","features":[]}"""

fun buildHexMapStyle(layerId: String, hexGeoJson: String): String {
    val tileUrl = DownloadLayers.tileUrlForLayer(layerId)
    return """
{
  "version": 8,
  "sources": {
    "$layerId": {
      "type": "raster",
      "tiles": ["$tileUrl"],
      "tileSize": 256,
      "minzoom": 0,
      "maxzoom": 18
    },
    "hexgrid": {
      "type": "geojson",
      "data": $hexGeoJson
    }
  },
  "layers": [
    {"id":"background","type":"background","paint":{"background-color":"#e8e8e8"}},
    {"id":"base","type":"raster","source":"$layerId"},
    {
      "id": "hex-fill",
      "type": "fill",
      "source": "hexgrid",
      "paint": {
        "fill-color": ["match", ["get", "status"],
          "downloaded", "#4CAF50",
          "downloading", "#2196F3",
          "rgba(0,0,0,0)"
        ],
        "fill-opacity": 0.4
      }
    },
    {
      "id": "hex-outline",
      "type": "line",
      "source": "hexgrid",
      "paint": {
        "line-color": ["match", ["get", "status"],
          "downloaded", "#2E7D32",
          "downloading", "#0D47A1",
          "#888888"
        ],
        "line-width": 1.5,
        "line-opacity": 0.7
      }
    }
  ]
}"""
}

fun buildHexGeoJson(
    hexes: List<HexGrid.Hex>,
    downloadedIds: Set<String>,
    downloadingId: String?
): String {
    val features = hexes.joinToString(",") { hex ->
        val vertices = HexGrid.hexVertices(hex)
        val coordsJson = vertices.joinToString(",") { "[${it.longitude},${it.latitude}]" }
        val status = when {
            hex.id == downloadingId -> "downloading"
            hex.id in downloadedIds -> "downloaded"
            else -> "none"
        }
        """{"type":"Feature","properties":{"hex_id":"${hex.id}","status":"$status"},"geometry":{"type":"Polygon","coordinates":[[$coordsJson]]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}
