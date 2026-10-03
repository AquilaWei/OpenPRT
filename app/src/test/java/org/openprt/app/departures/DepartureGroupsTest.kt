package org.openprt.app.departures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepartureGroupsTest {
    @Test
    fun groupByRoute_twoDirectionsOfOneRoute_shareOneGroup() {
        val groups = groupByRoute(listOf(P1_IN, P1_OUT))

        assertEquals(listOf(DepartureGroup("P1", listOf(P1_IN, P1_OUT))), groups)
    }

    @Test
    fun groupByRoute_routes_keepOrderOfTheirEarliestDeparture() {
        val groups = groupByRoute(listOf(C61_OUT, P1_OUT, C61_IN))

        assertEquals(listOf("61C", "P1"), groups.map { it.route })
    }

    @Test
    fun groupByRoute_rowsInGroup_areOrderedByDirection() {
        val groups = groupByRoute(listOf(P1_OUT, P1_IN))

        assertEquals(listOf(P1_IN, P1_OUT), groups.single().rows)
    }

    @Test
    fun oppositeDirectionOf_otherDirectionNearby_returnsIt() {
        assertEquals(P1_OUT, oppositeDirectionOf(P1_IN, listOf(C61_OUT, P1_IN, P1_OUT)))
    }

    @Test
    fun oppositeDirectionOf_onlyOtherRoutesGoTheOtherWay_returnsNull() {
        assertNull(oppositeDirectionOf(P1_IN, listOf(P1_IN, C61_OUT)))
    }

    @Test
    fun oppositeDirectionName_inbound_isOutbound() {
        assertEquals("OUTBOUND", oppositeDirectionName("INBOUND"))
    }

    @Test
    fun oppositeDirectionName_southbound_isNorthbound() {
        assertEquals("NORTHBOUND", oppositeDirectionName("SOUTHBOUND"))
    }

    @Test
    fun oppositeDirectionName_unknownName_isNull() {
        assertNull(oppositeDirectionName("LOOP"))
    }

    @Test
    fun directionLabel_trueTimeName_isCapitalizedWord() {
        assertEquals("Inbound", directionLabel("INBOUND"))
    }

    private companion object {
        fun departure(route: String, direction: String, minutes: Long) = DepartureItem(
            route = route,
            direction = direction,
            destination = "Somewhere",
            stopName = "Forbes Ave at Morewood Ave",
            walkMinutes = 2,
            minutesUntilDeparture = minutes,
            delayed = false,
            stopId = "7117",
            vehicleId = "$route-$direction"
        )

        val C61_OUT = departure("61C", "OUTBOUND", 3)
        val C61_IN = departure("61C", "INBOUND", 9)
        val P1_IN = departure("P1", "INBOUND", 4)
        val P1_OUT = departure("P1", "OUTBOUND", 6)
    }
}
