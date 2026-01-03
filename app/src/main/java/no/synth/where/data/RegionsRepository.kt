package no.synth.where.data

import android.content.Context

object RegionsRepository {
    private var _regions: List<Region>? = null

    fun getRegions(context: Context): List<Region> {
        if (_regions == null) {
            _regions = FylkeDataLoader.loadFylker(context)
        }
        return _regions ?: emptyList()
    }

    fun setRegionsForTest(regions: List<Region>?) {
        _regions = regions
    }
}
