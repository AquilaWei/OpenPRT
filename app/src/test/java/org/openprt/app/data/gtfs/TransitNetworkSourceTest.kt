package org.openprt.app.data.gtfs

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.TransitStop

class TransitNetworkSourceTest {
    @Test
    fun buildNetwork_stopWithCode_usesTheCodeAsTrueTimeStopId() {
        val network = buildNetwork(listOf(STEEL_PLAZA), emptyList(), emptyList())

        assertEquals(
            listOf(
                TransitStop(
                    "10",
                    "STEEL PLAZA STATION",
                    LatLng(40.439562, -79.995332),
                    "99994"
                )
            ),
            network.stops
        )
    }

    @Test
    fun buildNetwork_stopWithoutCode_usesTheStopIdAsTrueTimeStopId() {
        val network = buildNetwork(listOf(STEEL_PLAZA.copy(code = null)), emptyList(), emptyList())

        assertEquals("10", network.stops.single().trueTimeStopId)
    }

    @Test
    fun buildNetwork_stopTimesOfTwoTrips_givesEachTripItsOwnStops() {
        val network = buildNetwork(
            listOf(CMU, STEEL_PLAZA),
            listOf(trip("T1"), trip("T2")),
            listOf(
                stopTime("T1", 1, "8312", 25_200),
                stopTime("T1", 2, "10", 27_000),
                stopTime("T2", 1, "8312", 28_800),
                stopTime("T2", 2, "10", 30_600)
            )
        )

        assertEquals(
            listOf(listOf(25_200, 27_000), listOf(28_800, 30_600)),
            network.patterns.single().trips.map { trip -> trip.stops.map { it.departureSeconds } }
        )
    }

    @Test
    fun buildNetwork_tripWithOneStopTime_isLeftOut() {
        val network = buildNetwork(
            listOf(CMU, STEEL_PLAZA),
            listOf(trip("T1"), trip("T2")),
            listOf(
                stopTime("T1", 1, "8312", 25_200),
                stopTime("T1", 2, "10", 27_000),
                stopTime("T2", 1, "8312", 28_800)
            )
        )

        assertEquals(
            listOf("T1"),
            network.patterns.flatMap { pattern -> pattern.trips.map { it.tripId } }
        )
    }

    @Test
    fun buildNetwork_tripVisitingAnUnknownStop_isLeftOut() {
        val network = buildNetwork(
            listOf(CMU, STEEL_PLAZA),
            listOf(trip("T1"), trip("T2")),
            listOf(
                stopTime("T1", 1, "8312", 25_200),
                stopTime("T1", 2, "10", 27_000),
                stopTime("T2", 1, "8312", 28_800),
                stopTime("T2", 2, "E1", 30_600)
            )
        )

        assertEquals(
            listOf("T1"),
            network.patterns.flatMap { pattern -> pattern.trips.map { it.tripId } }
        )
    }

    private companion object {
        val CMU = StopEntity("8312", "8312", "CMU", 40.444557, -79.942791, 0)
        val STEEL_PLAZA = StopEntity("10", "99994", "STEEL PLAZA STATION", 40.439562, -79.995332, 1)

        fun trip(tripId: String) = TripEntity(tripId, "61C", "WK", null, 1)

        fun stopTime(tripId: String, sequence: Int, stopId: String, seconds: Int) = StopTimeEntity(
            tripId = tripId,
            stopSequence = sequence,
            stopId = stopId,
            arrivalSeconds = seconds,
            departureSeconds = seconds,
            pickupAllowed = true,
            dropOffAllowed = true
        )
    }
}
