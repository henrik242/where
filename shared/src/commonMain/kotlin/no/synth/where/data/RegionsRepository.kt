package no.synth.where.data

object RegionsRepository {
    private var _regions: List<Region>? = null

    fun getRegions(cacheDir: PlatformFile): List<Region> {
        if (_regions.isNullOrEmpty()) {
            _regions = FylkeDataLoader.loadFylker(cacheDir)
        }
        return _regions ?: emptyList()
    }

    fun reloadRegions(cacheDir: PlatformFile) {
        _regions = FylkeDataLoader.loadFylker(cacheDir)
    }

    fun setRegionsForTest(regions: List<Region>?) {
        _regions = regions
    }
}
