package no.synth.where.data

import org.osmdroid.util.BoundingBox

object RegionsRepository {
    val regions = listOf(
        Region(
            "Oslo",
            BoundingBox(60.13, 10.95, 59.81, 10.48)
        ),
        Region(
            "Vestland",
            BoundingBox(62.38, 8.32, 59.48, 4.55)
        ),
        Region(
            "Trøndelag",
            BoundingBox(65.47, 14.32, 62.26, 8.28)
        ),
        Region(
            "Nordland",
            BoundingBox(69.33, 18.16, 64.94, 11.56)
        ),
        Region(
            "Troms",
            BoundingBox(70.30, 22.88, 68.37, 15.59)
        ),
        Region(
            "Finnmark",
            BoundingBox(71.19, 31.17, 68.55, 21.26)
        ),
        Region(
            "Innlandet",
            BoundingBox(62.60, 12.50, 59.84, 7.90)
        ),
        Region(
            "Agder",
            BoundingBox(59.67, 9.60, 57.96, 6.56)
        ),
        Region(
            "Rogaland",
            BoundingBox(59.84, 7.21, 58.28, 4.85)
        )
    )
}

