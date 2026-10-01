package org.openprt.app.data.gtfs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GtfsFeedTest {
    @Test
    fun readGtfsFeed_withFixture_parsesStopsAndSkipsGenericNodes() {
        val feed = readGtfsFeed(zipOf(fixtureFeedFiles).inputStream())

        assertEquals(
            listOf(
                GtfsStop("10", "99994", "STEEL PLAZA STATION", 40.439562, -79.995332, 1),
                GtfsStop(
                    "8312",
                    "8312",
                    "FORBES AVE + MOREWOOD AVE \"CMU\"",
                    40.444557,
                    -79.942791,
                    0
                ),
                GtfsStop("2635", "2635", "FIFTH AVE + BELLEFIELD, OPP", 40.445770, -79.951416, 0)
            ),
            feed.stops
        )
    }

    @Test
    fun readGtfsFeed_withFixture_parsesRoutes() {
        val feed = readGtfsFeed(zipOf(fixtureFeedFiles).inputStream())

        assertEquals(
            listOf(
                GtfsRoute("61C", "61C", "MCKEESPORT-HOMESTEAD", 3, "CC00CC"),
                GtfsRoute("BLUE", "BLUE", null, 2, "00E9FF"),
                GtfsRoute("DQI", "DQI", "DUQUESNE INCLINE", 7, null)
            ),
            feed.routes
        )
    }

    @Test
    fun readGtfsFeed_withoutStopsFile_throwsFormatException() {
        val zip = zipOf(fixtureFeedFiles - "stops.txt")

        val error = assertThrows(GtfsFormatException::class.java) {
            readGtfsFeed(zip.inputStream())
        }

        assertEquals("stops.txt missing", error.message)
    }

    @Test
    fun readGtfsFeed_withStopMissingLatitude_throwsFormatException() {
        val zip = zipOf(
            fixtureFeedFiles +
                ("stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\n10,STEEL PLAZA,,-79.99\n")
        )

        assertThrows(GtfsFormatException::class.java) { readGtfsFeed(zip.inputStream()) }
    }
}
