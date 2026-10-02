package org.openprt.app.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Point
import org.openprt.app.details.BusPosition
import org.openprt.app.geo.LatLng

class BusMarkerTest {
    @Test
    fun toFeature_placesPointAtBusLocation() {
        val feature = BusPosition(LatLng(40.4406, -79.9959), headingDegrees = 90).toFeature()

        val point = feature.geometry() as Point
        assertEquals(listOf(-79.9959, 40.4406), listOf(point.longitude(), point.latitude()))
    }

    @Test
    fun toFeature_carriesHeadingForArrowRotation() {
        val feature = BusPosition(LatLng(40.4406, -79.9959), headingDegrees = 270).toFeature()

        assertEquals(270, feature.getNumberProperty("heading").toInt())
    }
}
