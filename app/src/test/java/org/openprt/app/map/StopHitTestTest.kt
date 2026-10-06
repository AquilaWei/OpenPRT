package org.openprt.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openprt.app.geo.LatLng

class StopHitTestTest {
    // A stand-in projection: one pixel per 0.0001 degree, x east and y south as on a screen.
    private val project: (LatLng) -> ScreenPoint = {
        ScreenPoint((it.longitude * 10_000).toFloat(), (-it.latitude * 10_000).toFloat())
    }

    private val forbes = StopMarker("7117", "Forbes Ave at Morewood", LatLng(40.0, -80.0))

    // 30 px east of forbes.
    private val fifth = StopMarker("2635", "Fifth Ave at Bellefield", LatLng(40.0, -79.997))

    @Test
    fun stopAt_tapOnStop_returnsThatStop() {
        val tap = ScreenPoint(-800_000f, -400_000f)

        assertEquals(forbes, stopAt(tap, listOf(forbes, fifth), 24f, project))
    }

    @Test
    fun stopAt_tapWithinRadius_returnsStop() {
        val tap = ScreenPoint(-800_000f, -400_020f)

        assertEquals(forbes, stopAt(tap, listOf(forbes), 24f, project))
    }

    @Test
    fun stopAt_tapOutsideRadius_returnsNull() {
        val tap = ScreenPoint(-800_000f, -400_025f)

        assertNull(stopAt(tap, listOf(forbes), 24f, project))
    }

    @Test
    fun stopAt_twoStopsInRange_returnsNearest() {
        // 20 px from forbes, 10 px from fifth.
        val tap = ScreenPoint(-799_980f, -400_000f)

        assertEquals(fifth, stopAt(tap, listOf(forbes, fifth), 24f, project))
    }

    @Test
    fun stopAt_noStops_returnsNull() {
        assertNull(stopAt(ScreenPoint(0f, 0f), emptyList(), 24f, project))
    }
}
