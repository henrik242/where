package no.synth.where.data

data class DownloadLayer(val id: String, val displayName: String, val tileUrl: String)

object DownloadLayers {
    val all: List<DownloadLayer> = listOf(
        DownloadLayer("kartverket", "Kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("toporaster", "Kartverket Toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("sjokartraster", "Kartverket Sjøkart", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("osm", "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        DownloadLayer("opentopomap", "OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png"),
        DownloadLayer("waymarkedtrails", "Waymarked Trails", "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png"),
    )

    fun tileUrlForLayer(layerName: String): String =
        all.find { it.id == layerName }?.tileUrl
            ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    fun getDownloadStyleJson(layerName: String): String {
        val tileUrl = tileUrlForLayer(layerName)
        return """
{
  "version": 8,
  "sources": {
    "$layerName": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["$tileUrl"],
      "tileSize": 256,
      "minzoom": 0,
      "maxzoom": 18
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
      "id": "$layerName-layer",
      "type": "raster",
      "source": "$layerName",
      "paint": {
        "raster-opacity": 1.0
      }
    }
  ]
}""".trimIndent()
    }
}
