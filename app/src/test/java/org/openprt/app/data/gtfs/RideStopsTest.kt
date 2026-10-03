package org.openprt.app.data.gtfs

import org.junit.Assert.assertEquals
import org.junit.Test

class RideStopsTest {
    @Test
    fun sliceBetween_bothStopsOnTrip_includesBothEnds() {
        assertEquals(
            listOf("b", "c", "d"),
            listOf("a", "b", "c", "d", "e").sliceBetween("b", "d") {
                it
            }
        )
    }

    @Test
    fun sliceBetween_loopPassesAlightingStopFirst_ridesToItsLaterVisit() {
        assertEquals(
            listOf("b", "c", "a"),
            listOf("a", "b", "c", "a").sliceBetween("b", "a") {
                it
            }
        )
    }

    @Test
    fun sliceBetween_boardingStopMissing_isEmpty() {
        assertEquals(emptyList<String>(), listOf("a", "b").sliceBetween("x", "b") { it })
    }

    @Test
    fun sliceBetween_alightingStopMissing_isEmpty() {
        assertEquals(emptyList<String>(), listOf("a", "b").sliceBetween("a", "x") { it })
    }
}
