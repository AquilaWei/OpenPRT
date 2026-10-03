package org.openprt.app.details

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineScrollTest {
    @Test
    fun firstTimelineRowIndex_busAFewStopsBeforeBoarding_opensOneRowAboveTheBus() {
        assertEquals(4, firstTimelineRowIndex(boardingIndex = 10, busIndex = 5))
    }

    @Test
    fun firstTimelineRowIndex_busFarAway_opensJustAboveBoardingStop() {
        assertEquals(18, firstTimelineRowIndex(boardingIndex = 20, busIndex = 3))
    }

    @Test
    fun firstTimelineRowIndex_busPastBoardingStop_opensJustAboveBoardingStop() {
        assertEquals(8, firstTimelineRowIndex(boardingIndex = 10, busIndex = 12))
    }

    @Test
    fun firstTimelineRowIndex_boardingStopNearTop_opensAtTop() {
        assertEquals(0, firstTimelineRowIndex(boardingIndex = 1, busIndex = null))
    }
}
