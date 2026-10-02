package org.openprt.app.data.gtfs

import java.io.StringReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GtfsFeedTest {
    private fun rowsOf(text: String): List<GtfsRow> = readGtfsTable(StringReader(text)).toList()

    @Test
    fun toStopEntity_withFixture_parsesStopsAndSkipsGenericNodes() {
        val stops = rowsOf(fixtureFeedFiles.getValue("stops.txt")).mapNotNull { it.toStopEntity() }

        assertEquals(
            listOf(
                StopEntity("10", "99994", "STEEL PLAZA STATION", 40.439562, -79.995332, 1),
                StopEntity(
                    "8312",
                    "8312",
                    "FORBES AVE + MOREWOOD AVE \"CMU\"",
                    40.444557,
                    -79.942791,
                    0
                ),
                StopEntity("2635", "2635", "FIFTH AVE + BELLEFIELD, OPP", 40.445770, -79.951416, 0)
            ),
            stops
        )
    }

    @Test
    fun toRouteEntity_withFixture_parsesRoutes() {
        val routes = rowsOf(fixtureFeedFiles.getValue("routes.txt")).map { it.toRouteEntity() }

        assertEquals(
            listOf(
                RouteEntity("61C", "61C", "MCKEESPORT-HOMESTEAD", 3, "CC00CC"),
                RouteEntity("BLUE", "BLUE", null, 2, "00E9FF"),
                RouteEntity("DQI", "DQI", "DUQUESNE INCLINE", 7, null)
            ),
            routes
        )
    }

    @Test
    fun toStopEntity_missingLatitude_throwsFormatException() {
        val row = rowsOf("stop_id,stop_name,stop_lat,stop_lon\n10,STEEL PLAZA,,-79.99\n").single()

        assertThrows(GtfsFormatException::class.java) { row.toStopEntity() }
    }

    @Test
    fun toTripEntity_withFixtureRow_parsesTrip() {
        val trip = rowsOf(fixtureFeedFiles.getValue("trips.txt")).first().toTripEntity()

        assertEquals(TripEntity("T1", "61C", "WK", "INBOUND-DOWNTOWN", 1), trip)
    }

    @Test
    fun toServiceCalendarEntity_withFixtureRow_parsesWeekdaysAndDates() {
        val calendar =
            rowsOf(fixtureFeedFiles.getValue("calendar.txt")).first().toServiceCalendarEntity()

        assertEquals(
            ServiceCalendarEntity(
                serviceId = "WK",
                monday = true,
                tuesday = true,
                wednesday = true,
                thursday = true,
                friday = true,
                saturday = false,
                sunday = false,
                startDate = LocalDate.of(2026, 6, 28),
                endDate = LocalDate.of(2026, 10, 24)
            ),
            calendar
        )
    }

    @Test
    fun toCalendarDateEntity_exceptionTypeTwo_isRemoval() {
        val row = rowsOf("service_id,date,exception_type\nWK,20260907,2\n").single()

        assertEquals(
            CalendarDateEntity("WK", LocalDate.of(2026, 9, 7), added = false),
            row.toCalendarDateEntity()
        )
    }

    @Test
    fun toCalendarDateEntity_unknownExceptionType_throwsFormatException() {
        val row = rowsOf("service_id,date,exception_type\nWK,20260907,3\n").single()

        assertThrows(GtfsFormatException::class.java) { row.toCalendarDateEntity() }
    }

    @Test
    fun parseGtfsTime_pastMidnight_countsFromStartOfServiceDay() {
        assertEquals(90_600, parseGtfsTime("25:10:00", rowNumber = 2))
    }

    @Test
    fun parseGtfsTime_singleDigitHour_isAccepted() {
        assertEquals(25_500, parseGtfsTime("7:05:00", rowNumber = 2))
    }

    @Test
    fun parseGtfsTime_singleDigitMinute_throwsFormatException() {
        assertThrows(GtfsFormatException::class.java) { parseGtfsTime("07:5:00", rowNumber = 2) }
    }

    @Test
    fun parseGtfsDate_isoDateWithDashes_throwsFormatException() {
        assertThrows(GtfsFormatException::class.java) { parseGtfsDate("2026-09-07", rowNumber = 2) }
    }
}
