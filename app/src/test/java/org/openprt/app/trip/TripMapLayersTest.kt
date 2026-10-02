package org.openprt.app.trip

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg

class TripMapLayersTest {
    @Test
    fun toMapLayers_walks_runFromOriginToStopsToDestination() {
        val layers = TRANSFER_TRIP.toMapLayers(ORIGIN, DESTINATION, emptyList())

        assertEquals(
            listOf(
                listOf(ORIGIN, CMU.location),
                listOf(CRAIG.location, FIFTH.location),
                listOf(STEEL_PLAZA.location, DESTINATION)
            ),
            layers.walks
        )
    }

    @Test
    fun toMapLayers_ridesWithStops_runThroughThem() {
        val stops = listOf(
            listOf(CMU.location, MIDDLE, CRAIG.location),
            listOf(FIFTH.location, STEEL_PLAZA.location)
        )

        val layers = TRANSFER_TRIP.toMapLayers(ORIGIN, DESTINATION, stops)

        assertEquals(stops, layers.rides)
    }

    @Test
    fun toMapLayers_rideWithoutStops_isStraightFromBoardingToAlighting() {
        val layers = TRANSFER_TRIP.toMapLayers(ORIGIN, DESTINATION, emptyList())

        assertEquals(
            listOf(
                listOf(CMU.location, CRAIG.location),
                listOf(FIFTH.location, STEEL_PLAZA.location)
            ),
            layers.rides
        )
    }

    @Test
    fun toMapLayers_marksBoardingStopsOfEveryRide() {
        val layers = TRANSFER_TRIP.toMapLayers(ORIGIN, DESTINATION, emptyList())

        assertEquals(listOf(CMU.location, FIFTH.location), layers.boardingStops)
    }

    @Test
    fun toMapLayers_marksAlightingStopsOfEveryRide() {
        val layers = TRANSFER_TRIP.toMapLayers(ORIGIN, DESTINATION, emptyList())

        assertEquals(listOf(CRAIG.location, STEEL_PLAZA.location), layers.alightingStops)
    }

    @Test
    fun toMapLayers_originAtTheStop_leavesOutTheEmptyWalk() {
        val trip = Itinerary(
            listOf(
                WalkLeg(null, CMU, 0.0, 25_200, 25_200),
                RideLeg("T1", "61C", null, CMU, CRAIG, 25_200, 25_800)
            )
        )

        val layers = trip.toMapLayers(CMU.location, CRAIG.location, emptyList())

        assertEquals(emptyList<List<LatLng>>(), layers.walks)
    }

    private companion object {
        val ORIGIN = LatLng(40.4450, -79.9500)
        val DESTINATION = LatLng(40.4400, -80.0000)
        val MIDDLE = LatLng(40.4440, -79.9500)
        val CMU = TransitStop("s1", "Forbes Ave at Morewood", LatLng(40.4443, -79.9532))
        val CRAIG = TransitStop("s2", "Fifth Ave at Craig", LatLng(40.4447, -79.9483))
        val FIFTH = TransitStop("s3", "Fifth Ave at Bellefield", LatLng(40.4450, -79.9510))
        val STEEL_PLAZA = TransitStop("s4", "Steel Plaza", LatLng(40.4406, -79.9959))

        /** Walk, 61C, walk to transfer, P1, walk. */
        val TRANSFER_TRIP = Itinerary(
            listOf(
                WalkLeg(null, CMU, 200.0, 25_000, 25_200),
                RideLeg("T1", "61C", null, CMU, CRAIG, 25_200, 25_800),
                WalkLeg(CRAIG, FIFTH, 150.0, 25_800, 26_000),
                RideLeg("T2", "P1", null, FIFTH, STEEL_PLAZA, 26_100, 27_000),
                WalkLeg(STEEL_PLAZA, null, 100.0, 27_000, 27_100)
            )
        )
    }
}
