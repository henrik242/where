package no.synth.where.data

data class DownloadLayer(
    val id: String,
    val displayName: String,
    val tileUrl: String,
    val minZoom: Int = 0,
    val maxZoom: Int = 18,
    val isOverlay: Boolean = false
)

object DownloadLayers {
    val all: List<DownloadLayer> = listOf(
        DownloadLayer("kartverket", "Kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("toporaster", "Kartverket Toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("sjokartraster", "Kartverket Sjøkart", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("mapant", "MapAnt", "https://mapant.no/tiles/osm/{z}/{x}/{y}.png"),
        DownloadLayer("osm", "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        DownloadLayer("opentopomap", "OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png"),
        DownloadLayer("waymarkedtrails", "Waymarked Trails", "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png", isOverlay = true),
        DownloadLayer("avalanchezones", "Avalanche Zones (NVE)", "https://gis3.nve.no/arcgis/rest/services/wmts/Bratthet_med_utlop_2024/MapServer/tile/{z}/{y}/{x}", minZoom = 6, maxZoom = 19, isOverlay = true),
        DownloadLayer("terrain", "Terrain (Hillshade)", "https://s3.amazonaws.com/elevation-tiles-prod/normal/{z}/{x}/{y}.png", maxZoom = 15, isOverlay = true),
    )

    fun tileUrlForLayer(layerName: String): String =
        all.find { it.id == layerName }?.tileUrl
            ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    private const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    fun getDownloadStyleJson(layerName: String): String {
        val layer = all.find { it.id == layerName }
        val tileUrl = layer?.tileUrl ?: OSM_TILE_URL
        val minZoom = layer?.minZoom ?: 0
        val maxZoom = layer?.maxZoom ?: 18
        val isOverlay = layer?.isOverlay == true

        val osmSource = if (isOverlay) """
    "osm": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["$OSM_TILE_URL"],
      "tileSize": 256,
      "minzoom": 0,
      "maxzoom": 18
    },""" else ""

        val osmLayer = if (isOverlay) """
    {
      "id": "osm-layer",
      "type": "raster",
      "source": "osm",
      "paint": {
        "raster-opacity": 1.0
      }
    },""" else ""

        return """
{
  "version": 8,
  "sources": {$osmSource
    "$layerName": {
      "type": "raster",
      "scheme": "xyz",
      "tiles": ["$tileUrl"],
      "tileSize": 256,
      "minzoom": $minZoom,
      "maxzoom": $maxZoom
    }
  },
  "layers": [
    {
      "id": "background",
      "type": "background",
      "paint": {
        "background-color": "#f0f0f0"
      }
    },$osmLayer
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
