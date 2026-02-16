package no.synth.where.data

data class DownloadState(
    val region: Region? = null,
    val layerName: String? = null,
    val progress: Int = 0,
    val isDownloading: Boolean = false
)
