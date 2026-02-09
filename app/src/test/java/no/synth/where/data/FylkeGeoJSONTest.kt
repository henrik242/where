package no.synth.where.data

import org.junit.Test
import org.junit.Assert.*

class FylkeGeoJSONTest {

    @Test
    fun fromGeonorge_parsesSimpleFeatureCollection() {
        val geojson = """{
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "properties": {"fylkesnavn": "Oslo"},
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[10.6, 59.8], [10.8, 59.8], [10.8, 60.0], [10.6, 60.0], [10.6, 59.8]]]
                }
            }]
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals("FeatureCollection", result.type)
        assertEquals(1, result.features.size)
        assertEquals("Oslo", result.features[0].properties["fylkesnavn"])
        assertEquals("Polygon", result.features[0].geometry.type)
    }

    @Test
    fun fromGeonorge_handlesNestedFylkeKey() {
        val geojson = """{
            "Fylke": {
                "type": "FeatureCollection",
                "features": [{
                    "type": "Feature",
                    "properties": {"name": "Vestland"},
                    "geometry": {"type": "Polygon", "coordinates": [[[5.0, 60.0], [5.5, 60.0], [5.5, 61.0], [5.0, 61.0], [5.0, 60.0]]]}
                }]
            }
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals(1, result.features.size)
        assertEquals("Vestland", result.features[0].properties["name"])
    }

    @Test
    fun fromGeonorge_handlesNonPrimitiveProperties() {
        // Regression test for the JsonArray crash (FylkeDataLoader.kt:27)
        val geojson = """{
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "properties": {
                    "fylkesnavn": "Trøndelag",
                    "codes": [1, 2, 3],
                    "nested": {"key": "value"},
                    "nullProp": null
                },
                "geometry": {
                    "type": "Polygon",
                    "coordinates": [[[10.0, 63.0], [11.0, 63.0], [11.0, 64.0], [10.0, 64.0], [10.0, 63.0]]]
                }
            }]
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals(1, result.features.size)
        assertEquals("Trøndelag", result.features[0].properties["fylkesnavn"])
        // Non-primitive values should be toString'd, not crash
        assertNotNull(result.features[0].properties["codes"])
        assertNotNull(result.features[0].properties["nested"])
    }

    @Test
    fun fromGeonorge_handlesMultiPolygon() {
        val geojson = """{
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "properties": {"fylkesnavn": "Nordland"},
                "geometry": {
                    "type": "MultiPolygon",
                    "coordinates": [
                        [[[14.0, 67.0], [15.0, 67.0], [15.0, 68.0], [14.0, 68.0], [14.0, 67.0]]],
                        [[[15.5, 68.0], [16.0, 68.0], [16.0, 69.0], [15.5, 69.0], [15.5, 68.0]]]
                    ]
                }
            }]
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals("MultiPolygon", result.features[0].geometry.type)
    }

    @Test
    fun fromGeonorge_handlesEmptyFeatures() {
        val geojson = """{"type": "FeatureCollection", "features": []}"""
        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertTrue(result.features.isEmpty())
    }

    @Test
    fun fromGeonorge_handlesMultipleFeatures() {
        val geojson = """{
            "type": "FeatureCollection",
            "features": [
                {"type": "Feature", "properties": {"fylkesnavn": "Oslo"}, "geometry": {"type": "Polygon", "coordinates": [[[10.6, 59.8], [10.8, 60.0], [10.6, 59.8]]]}},
                {"type": "Feature", "properties": {"fylkesnavn": "Bergen"}, "geometry": {"type": "Polygon", "coordinates": [[[5.0, 60.0], [5.5, 61.0], [5.0, 60.0]]]}}
            ]
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals(2, result.features.size)
        assertEquals("Oslo", result.features[0].properties["fylkesnavn"])
        assertEquals("Bergen", result.features[1].properties["fylkesnavn"])
    }

    @Test
    fun fromGeonorge_defaultsTypeToFeatureCollection() {
        val geojson = """{"features": []}"""
        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertEquals("FeatureCollection", result.type)
    }

    @Test
    fun fromGeonorge_handlesNullProperties() {
        val geojson = """{
            "type": "FeatureCollection",
            "features": [{
                "type": "Feature",
                "geometry": {"type": "Polygon", "coordinates": [[[10.0, 59.0], [11.0, 60.0], [10.0, 59.0]]]}
            }]
        }"""

        val result = FylkeGeoJSON.fromGeonorge(geojson)
        assertTrue(result.features[0].properties.isEmpty())
    }
}
