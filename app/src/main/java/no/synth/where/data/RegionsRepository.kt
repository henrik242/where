package no.synth.where.data

import org.maplibre.android.geometry.LatLngBounds

object RegionsRepository {
    val regions = listOf(
        Region(
            "Oslo",
            LatLngBounds.from(60.13, 10.95, 59.81, 10.48)
        ),
        Region(
            "Vestland",
            LatLngBounds.from(62.38, 8.32, 59.48, 4.55)
        ),
        Region(
            "Trøndelag",
            LatLngBounds.from(65.47, 14.32, 62.26, 8.28)
        ),
        Region(
            "Nordland",
            LatLngBounds.from(69.33, 18.16, 64.94, 11.56)
        ),
        Region(
            "Troms",
            LatLngBounds.from(70.30, 22.88, 68.37, 15.59)
        ),
        Region(
            "Finnmark",
            LatLngBounds.from(71.19, 31.17, 68.55, 21.26)
        ),
        Region(
            "Innlandet",
            LatLngBounds.from(62.60, 12.50, 59.84, 7.90)
        ),
        Region(
            "Agder",
            LatLngBounds.from(59.67, 9.60, 57.96, 6.56)
        ),
        Region(
            "Rogaland",
            LatLngBounds.from(59.84, 7.21, 58.28, 4.85)
        )
    )
}

