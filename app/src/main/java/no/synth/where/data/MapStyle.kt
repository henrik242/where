package no.synth.where.data

object MapStyle {
    const val KARTVERKET_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "kartverket": {
      "type": "raster",
      "tiles": [
        "https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png"
      ],
      "tileSize": 256,
      "attribution": "&copy; Kartverket"
    }
  },
  "layers": [
    {
      "id": "kartverket-layer",
      "type": "raster",
      "source": "kartverket",
      "paint": {}
    }
  ]
}
"""
}

