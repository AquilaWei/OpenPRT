package org.openprt.app.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    // Steel Plaza station (Downtown) and Fifth Ave + Bellefield (Oakland), from the PRT GTFS feed.
    private val downtown = LatLng(40.439562, -79.995332)
    private val oakland = LatLng(40.445770, -79.951416)

    @Test
    fun haversineMeters_downtownToOakland_isWithinOnePercentOfWgs84Geodesic() {
        // 3789.06 m is the WGS84 ellipsoid distance computed with Vincenty's formula.
        assertEquals(3789.06, haversineMeters(downtown, oakland), 37.89)
    }

    @Test
    fun haversineMeters_samePoint_isZero() {
        assertEquals(0.0, haversineMeters(downtown, downtown), 0.0)
    }

    @Test
    fun haversineMeters_isSymmetric() {
        assertEquals(haversineMeters(downtown, oakland), haversineMeters(oakland, downtown), 1e-9)
    }

    @Test
    fun boundingBoxAround_400mInPittsburgh_matchesSphericalCapExtent() {
        val box = BoundingBox.around(LatLng(40.444, -79.945), 400.0)

        assertEquals(40.4404027, box.minLatitude, 1e-6)
        assertEquals(40.4475973, box.maxLatitude, 1e-6)
        assertEquals(-79.9497268, box.minLongitude, 1e-6)
        assertEquals(-79.9402732, box.maxLongitude, 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun boundingBoxAround_negativeRadius_throws() {
        BoundingBox.around(downtown, -1.0)
    }

    @Test
    fun pittsburghArea_downtown_isInside() {
        assertTrue(downtown in PITTSBURGH_AREA)
    }

    @Test
    fun pittsburghArea_philadelphia_isOutside() {
        assertFalse(LatLng(39.952583, -75.165222) in PITTSBURGH_AREA)
    }

    @Test
    fun boundingBoxContains_pointOnEdge_isInside() {
        val box = BoundingBox(40.0, 41.0, -80.0, -79.0)

        assertTrue(LatLng(41.0, -80.0) in box)
    }
}
