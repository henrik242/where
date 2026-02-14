package no.synth.where.data

data class RegionTileInfo(
    val totalTiles: Int,
    val downloadedTiles: Int,
    val downloadedSize: Long,
    val isFullyDownloaded: Boolean
)
