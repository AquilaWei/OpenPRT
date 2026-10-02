package org.openprt.app.data.gtfs

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceCalendarTest {
    private val weekdays = ServiceCalendarEntity(
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
    )
    private val saturdays = weekdays.copy(
        serviceId = "SA",
        monday = false,
        tuesday = false,
        wednesday = false,
        thursday = false,
        friday = false,
        saturday = true
    )
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun activeServiceIds_noExceptions_followsWeekdayPattern() {
        val active = activeServiceIds(monday, listOf(weekdays, saturdays), emptyList())

        assertEquals(setOf("WK"), active)
    }

    @Test
    fun activeServiceIds_removalException_dropsScheduledService() {
        val active = activeServiceIds(
            monday,
            listOf(weekdays, saturdays),
            listOf(CalendarDateEntity("WK", monday, added = false))
        )

        assertEquals(emptySet<String>(), active)
    }

    @Test
    fun activeServiceIds_additionException_addsServiceOffItsWeekdays() {
        val active = activeServiceIds(
            monday,
            listOf(weekdays, saturdays),
            listOf(CalendarDateEntity("SA", monday, added = true))
        )

        assertEquals(setOf("WK", "SA"), active)
    }

    @Test
    fun activeServiceIds_additionForServiceWithoutCalendarRow_addsIt() {
        val active = activeServiceIds(
            monday,
            emptyList(),
            listOf(CalendarDateEntity("HOLIDAY", monday, added = true))
        )

        assertEquals(setOf("HOLIDAY"), active)
    }

    @Test
    fun activeServiceIds_exceptionOnAnotherDate_isIgnored() {
        val active = activeServiceIds(
            monday,
            listOf(weekdays),
            listOf(CalendarDateEntity("WK", LocalDate.of(2026, 9, 8), added = false))
        )

        assertEquals(setOf("WK"), active)
    }

    @Test
    fun activeServiceIds_dayAfterEndDate_isInactive() {
        val active = activeServiceIds(LocalDate.of(2026, 10, 26), listOf(weekdays), emptyList())

        assertEquals(emptySet<String>(), active)
    }

    @Test
    fun serviceTime_pastMidnight_isEarlyNextMorning() {
        // 25:10:00 on Oct 1 is 1:10 a.m. EDT on Oct 2.
        assertEquals(
            Instant.parse("2026-10-02T05:10:00Z"),
            serviceTime(LocalDate.of(2026, 10, 1), 90_600)
        )
    }

    @Test
    fun serviceTime_dayClocksFallBack_countsFromNoonMinusTwelveHours() {
        // Noon EST is 17:00Z; 12 hours earlier is 05:00Z, which is 1:00 a.m. EDT.
        assertEquals(
            Instant.parse("2026-11-01T05:00:00Z"),
            serviceTime(LocalDate.of(2026, 11, 1), 0)
        )
    }

    @Test
    fun departureTime_combinesServiceDateAndSeconds() {
        val departure = ScheduledDeparture(
            "T4",
            "61C",
            null,
            2,
            LocalDate.of(2026, 10, 1),
            departureSeconds = 89_400
        )

        assertEquals(Instant.parse("2026-10-02T04:50:00Z"), departure.departureTime)
    }
}
