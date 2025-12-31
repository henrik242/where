package no.synth.where.data

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

object KartverketTileSource : OnlineTileSourceBase(
    "Kartverket",
    0, 18, 256, "png",
    arrayOf("https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + ".png"
    }
}

