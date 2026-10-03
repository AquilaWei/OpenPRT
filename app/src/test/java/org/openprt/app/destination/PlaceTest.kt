package org.openprt.app.destination

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng

class PlaceTest {
    @Test
    fun label_placeWithAddress_isNameThenAddress() {
        val place = Place(
            "Cathedral of Learning",
            "",
            LatLng(40.444, -79.953),
            address = "4200 Fifth Avenue"
        )

        assertEquals("Cathedral of Learning · 4200 Fifth Avenue", place.label)
    }

    @Test
    fun label_placeWithoutAddress_isName() {
        val place = Place("4400 Forbes Avenue", "Pittsburgh", LatLng(40.44, -79.95))

        assertEquals("4400 Forbes Avenue", place.label)
    }
}
