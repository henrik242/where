package no.synth.where.data

import no.synth.where.data.geo.LatLngBounds
import no.synth.where.ui.map.MapLayer
import no.synth.where.ui.map.NveOverlay
import kotlin.math.ceil

/**
 * A layer offered for offline download. Vector layers carry a TileJSON URL in [tileUrl] rather than
 * an `{z}/{x}/{y}` template, so their styles come from [MapStyle] instead of being built here.
 */
data class DownloadLayer(
    val id: String,
    val displayName: String,
    val tileUrl: String,
    val minZoom: Int = 0,
    val maxZoom: Int = DEFAULT_MAX_ZOOM,
    val isOverlay: Boolean = false,
    val isVector: Boolean = false
)

private const val DEFAULT_MAX_ZOOM = 18

object DownloadLayers {
    const val OSM_PATHS_ID = "osmpaths"

    private fun nveLayer(overlay: NveOverlay, displayName: String) = DownloadLayer(
        overlay.sourceId,
        displayName,
        overlay.tileUrl,
        minZoom = NveOverlay.MIN_ZOOM,
        maxZoom = NveOverlay.MAX_ZOOM,
        isOverlay = true,
    )

    val all: List<DownloadLayer> = listOf(
        DownloadLayer("kartverket", "Kartverket", "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("toporaster", "Kartverket Toporaster", "https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("sjokartraster", "Kartverket Sjøkart", "https://cache.kartverket.no/v1/wmts/1.0.0/sjokartraster/default/webmercator/{z}/{y}/{x}.png"),
        DownloadLayer("mapant", "MapAnt", "https://mapant.no/tiles/osm/{z}/{x}/{y}.png", maxZoom = 16),
        DownloadLayer("satellite", "Satellite (Sentinel-2)", "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2025_3857/default/g/{z}/{y}/{x}.jpg", maxZoom = 14),
        DownloadLayer("osm", "OpenStreetMap", "https://tile.openstreetmap.org/{z}/{x}/{y}.png"),
        DownloadLayer("opentopomap", "OpenTopoMap", "https://tile.opentopomap.org/{z}/{x}/{y}.png", maxZoom = 17),
        DownloadLayer("waymarkedtrails", "Waymarked Trails", "https://tile.waymarkedtrails.org/hiking/{z}/{x}/{y}.png", isOverlay = true),
        DownloadLayer(OSM_PATHS_ID, "Paths (OSM)", MapStyle.OSM_PATHS_TILEJSON_URL, maxZoom = 14, isOverlay = true, isVector = true),
        nveLayer(NveOverlay.STEEPNESS_RUNOUT, "Steepness + Runout (NVE)"),
        nveLayer(NveOverlay.STEEPNESS, "Steepness (NVE)"),
    )

    fun tileUrlForLayer(layerName: String): String =
        all.find { it.id == layerName }?.tileUrl
            ?: "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    /**
     * The zoom a region download should actually stop at: the user's requested detail level,
     * clamped to the layer's own [DownloadLayer.maxZoom] so we never request tiles the source
     * doesn't have (e.g. satellite tops out at 14, MapAnt at 16).
     */
    fun effectiveMaxZoom(layerId: String, requestedMaxZoom: Int): Int {
        val layerMax = all.find { it.id == layerId }?.maxZoom ?: DEFAULT_MAX_ZOOM
        return minOf(requestedMaxZoom, layerMax)
    }

    // MapLibre fetches 20 tiles at a time and Kartverket answers in ~0.4 s each.
    private const val TYPICAL_TILES_PER_SECOND = 40

    /** Expected minutes to download [tiles], rounded up. */
    fun estimatedDownloadMinutes(tiles: Int): Int =
        ceil(tiles / (TYPICAL_TILES_PER_SECOND * 60.0)).toInt().coerceAtLeast(1)

    /**
     * Tiles a pack of [layerId] over [bounds] fetches. MapLibre counts zoom in 512px tiles, so 256px
     * raster sources come one zoom deeper than requested; overlays also fetch the OSM raster base.
     */
    fun packTileCount(layerId: String, bounds: LatLngBounds, minZoom: Int, maxZoom: Int): Int {
        val layer = all.find { it.id == layerId }
        fun count(sourceMinZoom: Int, sourceMaxZoom: Int, zoomOffset: Int): Int {
            val from = maxOf(minZoom + zoomOffset, sourceMinZoom)
            val to = minOf(maxZoom + zoomOffset, sourceMaxZoom)
            return if (from > to) 0 else TileUtils.estimateTileCount(bounds, from, to)
        }
        val layerZoomOffset = if (layer?.isVector == true) 0 else 1
        val layerTiles = count(layer?.minZoom ?: 0, layer?.maxZoom ?: DEFAULT_MAX_ZOOM, layerZoomOffset)
        val baseTiles = if (layer?.isOverlay == true) count(0, DEFAULT_MAX_ZOOM, zoomOffset = 1) else 0
        return layerTiles + baseTiles
    }

    private const val OSM_TILE_URL = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"

    fun getDownloadStyleJson(layerName: String): String {
        val layer = all.find { it.id == layerName }

        // Vector layers reuse the live style, so the pack stores tiles under exactly the URLs the
        // map will later look up in the offline cache. Its OSM base doubles as the backdrop that
        // raster overlays get below.
        if (layer?.isVector == true) {
            return MapStyle.getStyle(selectedLayer = MapLayer.OSM, showOsmPaths = true)
        }

        // packTileCount assumes these 256px tile sizes; change both together.
        val tileUrl = layer?.tileUrl ?: OSM_TILE_URL
        val minZoom = layer?.minZoom ?: 0
        val maxZoom = layer?.maxZoom ?: DEFAULT_MAX_ZOOM
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
