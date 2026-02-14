package no.synth.where.data

object SkiTrailRepository {
    private var _skiTrails: List<SkiTrail>? = null

    fun getSkiTrails(cacheDir: PlatformFile): List<SkiTrail> {
        if (_skiTrails == null) {
            _skiTrails = SkiTrailDataLoader.loadSkiTrails(cacheDir)
        }
        return _skiTrails ?: emptyList()
    }

    fun reloadSkiTrails(cacheDir: PlatformFile) {
        _skiTrails = SkiTrailDataLoader.loadSkiTrails(cacheDir)
    }
}
