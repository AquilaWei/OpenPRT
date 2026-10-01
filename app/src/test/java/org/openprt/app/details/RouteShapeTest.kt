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

    private companion object {
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
