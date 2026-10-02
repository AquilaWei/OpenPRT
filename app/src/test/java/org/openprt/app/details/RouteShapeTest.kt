package org.openprt.app.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.PatternPoint
import org.openprt.app.data.truetime.PatternStop
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

class RouteShapeTest {
    @Test
    fun toRouteShape_fixturePattern_lineVisitsEveryPointInOrder() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "7117")

        assertEquals(
            listOf(
                LatLng(40.440878, -79.999141),
                LatLng(40.44121, -79.99502),
                LatLng(40.444567, -79.942862)
            ),
            shape.line
        )
    }

    @Test
    fun toRouteShape_fixturePattern_stopsSkipWaypoints() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "7117")

        assertEquals(
            listOf(
                StopMarker("20690", "Fifth Ave at Wood St", LatLng(40.440878, -79.999141)),
                StopMarker(
                    "7117",
                    "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                    LatLng(40.444567, -79.942862)
                )
            ),
            shape.stops
        )
    }

    @Test
    fun toRouteShape_boardingStopOnPattern_isMarked() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "7117")

        assertEquals(
            StopMarker(
                "7117",
                "Forbes Ave at Morewood Ave (Carnegie Mellon)",
                LatLng(40.444567, -79.942862)
            ),
            shape.boardingStop
        )
    }

    @Test
    fun toRouteShape_boardingStopOnPattern_knowsHowFarAlongItIs() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "7117")

        assertEquals(18620.0, shape.boardingDistanceFeet)
    }

    @Test
    fun toRouteShape_boardingStopNotOnPattern_hasNoBoardingDistance() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "99994")

        assertNull(shape.boardingDistanceFeet)
    }

    @Test
    fun toRouteShape_boardingStopNotOnPattern_hasNoBoardingStop() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "99994")

        assertNull(shape.boardingStop)
    }

    @Test
    fun toRouteShape_pointsOutOfOrder_followsSequence() {
        val pattern = Pattern(
            id = 1,
            lengthFeet = 100.0,
            routeDirection = "INBOUND",
            points = listOf(
                PatternPoint(2, 40.2, -79.2, null),
                PatternPoint(1, 40.1, -79.1, null)
            )
        )

        val shape = pattern.toRouteShape(boardingStopId = "7117")

        assertEquals(listOf(LatLng(40.1, -79.1), LatLng(40.2, -79.2)), shape.line)
    }

    @Test
    fun toRouteShape_loopVisitsBoardingStopTwice_boardsAtFirstVisit() {
        val pattern = Pattern(
            id = 1,
            lengthFeet = 100.0,
            routeDirection = "LOOP",
            points = listOf(
                PatternPoint(1, 40.1, -79.1, PatternStop("7117", "First visit", 0.0)),
                PatternPoint(2, 40.2, -79.2, PatternStop("7117", "Second visit", 50.0))
            )
        )

        val shape = pattern.toRouteShape(boardingStopId = "7117")

        assertEquals("First visit", shape.boardingStop?.name)
    }

    @Test
    fun toRouteShape_fixturePattern_knowsHowFarAlongEachStopIs() {
        val shape = FIXTURE_PATTERN.toRouteShape(boardingStopId = "7117")

        assertEquals(listOf(0.0, 18620.0), shape.stopDistancesFeet)
    }

    @Test
    fun progressOf_busBetweenStops_countsStopsUpToBoardingStop() {
        assertEquals(BusProgress(passedStops = 1, stopsAway = 2), FOUR_STOPS.progressOf(50.0))
    }

    @Test
    fun progressOf_busAtAStop_hasNotPassedIt() {
        assertEquals(BusProgress(passedStops = 1, stopsAway = 1), FOUR_STOPS.progressOf(100.0))
    }

    @Test
    fun progressOf_busAtBoardingStop_isNoStopsAway() {
        assertEquals(BusProgress(passedStops = 2, stopsAway = 0), FOUR_STOPS.progressOf(300.0))
    }

    @Test
    fun progressOf_busPastBoardingStop_hasNoStopsAway() {
        assertEquals(BusProgress(passedStops = 3, stopsAway = null), FOUR_STOPS.progressOf(350.0))
    }

    @Test
    fun progressOf_stopDistancesUnknown_isNull() {
        assertNull(FOUR_STOPS.copy(stopDistancesFeet = emptyList()).progressOf(150.0))
    }

    private companion object {
        val BOARDING = StopMarker("3", "Third", LatLng(40.3, -79.3))

        /** Stops at 0, 100, 300 (boarding) and 400 ft along the pattern. */
        val FOUR_STOPS = RouteShape(
            line = emptyList(),
            stops = listOf(
                StopMarker("1", "First", LatLng(40.1, -79.1)),
                StopMarker("2", "Second", LatLng(40.2, -79.2)),
                BOARDING,
                StopMarker("4", "Fourth", LatLng(40.4, -79.4))
            ),
            boardingStop = BOARDING,
            boardingDistanceFeet = 300.0,
            stopDistancesFeet = listOf(0.0, 100.0, 300.0, 400.0)
        )

        /** The pattern in truetime/getpatterns.json, as TrueTimeClientTest shows it parses. */
        val FIXTURE_PATTERN = Pattern(
            id = 4512,
            lengthFeet = 85432.0,
            routeDirection = "OUTBOUND",
            points = listOf(
                PatternPoint(
                    1,
                    40.440878,
                    -79.999141,
                    PatternStop("20690", "Fifth Ave at Wood St", 0.0)
                ),
                PatternPoint(2, 40.44121, -79.99502, null),
                PatternPoint(
                    3,
                    40.444567,
                    -79.942862,
                    PatternStop("7117", "Forbes Ave at Morewood Ave (Carnegie Mellon)", 18620.0)
                )
            )
        )
    }
}
