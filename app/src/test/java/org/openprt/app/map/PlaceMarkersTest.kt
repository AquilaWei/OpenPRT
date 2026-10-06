package org.openprt.app.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng

class PlaceMarkersTest {
    private val user = LatLng(40.4406, -79.9959)
    private val cathedral = LatLng(40.4443, -79.9532)
    private val cmu = LatLng(40.4433, -79.9436)

    @Test
    fun cameraPoints_noOriginOrDestination_followsUser() {
        assertEquals(listOf(user), cameraPoints(user, origin = null, destination = null))
    }

    @Test
    fun cameraPoints_destinationOnly_framesUserAndDestination() {
        assertEquals(listOf(user, cmu), cameraPoints(user, origin = null, destination = cmu))
    }

    @Test
    fun cameraPoints_chosenOrigin_framesOriginAndDestinationNotUser() {
        assertEquals(listOf(cathedral, cmu), cameraPoints(user, cathedral, cmu))
    }

    @Test
    fun cameraPoints_chosenOriginWithoutDestination_followsOrigin() {
        assertEquals(listOf(cathedral), cameraPoints(user, cathedral, destination = null))
    }

    @Test
    fun cameraPoints_destinationAtUser_followsUser() {
        assertEquals(listOf(user), cameraPoints(user, origin = null, destination = user))
    }
}
