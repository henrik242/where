package no.synth.where.data

import java.io.File

object RegionsRepository {
    private var _regions: List<Region>? = null

    fun getRegions(cacheDir: File): List<Region> {
        if (_regions == null) {
            _regions = FylkeDataLoader.loadFylker(cacheDir)
        }
        return _regions ?: emptyList()
    }

    fun reloadRegions(cacheDir: File) {
        _regions = FylkeDataLoader.loadFylker(cacheDir)
    }

    fun setRegionsForTest(regions: List<Region>?) {
        _regions = regions
    }
}
