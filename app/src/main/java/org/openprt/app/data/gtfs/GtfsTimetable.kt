package org.openprt.app.data.gtfs

import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** PRT's agency_timezone; every GTFS time and date in its feed is local to it. */
val PRT_TIME_ZONE: ZoneId = ZoneId.of("America/New_York")

/** A scheduled bus or train leaving a stop, from the GTFS timetable (not a live prediction). */
data class ScheduledDeparture(
    val tripId: String,
    val routeId: String,
    val headsign: String?,
    val stopSequence: Int,
    val serviceDate: LocalDate,
    /** Seconds after the start of [serviceDate]; can pass 24 hours, see [serviceTime]. */
    val departureSeconds: Int
) {
    val departureTime: Instant get() = serviceTime(serviceDate, departureSeconds)
}

/** The days the imported timetable covers; an interface so screens can be tested with a fake. */
fun interface TimetableDatesSource {
    /**
     * From the first to the last day the timetable runs any service, both included, or null when
     * no timetable has been imported. Days inside the range may still have no service.
     */
    suspend fun dates(): ClosedRange<LocalDate>?
}

/**
 * [TimetableDatesSource] over the imported GTFS calendar in [database]. Both ends are read in one
 * transaction, so a background import committing meanwhile cannot pair one feed's first day with
 * another's last.
 */
class RoomTimetableDatesSource(
    private val database: GtfsDatabase,
    // Tests wrap the DAO to act between its reads.
    private val dao: GtfsDao = database.gtfsDao()
) : TimetableDatesSource {
    override suspend fun dates(): ClosedRange<LocalDate>? = database.withTransaction {
        val first = dao.getFirstServiceDate() ?: return@withTransaction null
        val last = dao.getLastServiceDate() ?: return@withTransaction null
        first..last
    }
}

/**
 * Looks up scheduled departures in the imported GTFS timetable.
 *
 * Queries one service day at a time. A trip of yesterday's service day can still run after
 * midnight (times past 24:00:00), so a caller asking "what leaves after 00:30 today" should also
 * ask for yesterday's service day after 24:30:00. The reads of one call share a transaction, so a
 * background import committing meanwhile cannot mix one feed's calendar with another's trips.
 */
class GtfsTimetable(
    private val database: GtfsDatabase,
    private val dao: GtfsDao = database.gtfsDao()
) {
    /**
     * Up to [limit] departures from [stopId] at or after [afterSeconds] on [serviceDate],
     * earliest first (ties by route, then trip). Skips stops where riders cannot board, such as
     * the last stop of a trip. Empty when nothing runs that day or the timetable is empty.
     */
    suspend fun departuresAfter(
        stopId: String,
        serviceDate: LocalDate,
        afterSeconds: Int,
        limit: Int = DEFAULT_LIMIT
    ): List<ScheduledDeparture> = database.withTransaction {
        val serviceIds = activeServiceIds(
            serviceDate,
            dao.getCalendarsCovering(serviceDate),
            dao.getCalendarDatesOn(serviceDate)
        )
        if (serviceIds.isEmpty()) return@withTransaction emptyList()
        dao.getDeparturesAfter(stopId, afterSeconds, serviceIds, limit).map {
            ScheduledDeparture(
                tripId = it.tripId,
                routeId = it.routeId,
                headsign = it.headsign,
                stopSequence = it.stopSequence,
                serviceDate = serviceDate,
                departureSeconds = it.departureSeconds
            )
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 20
    }
}

/**
 * The services that run on [date]: those whose calendar row covers the date and weekday, plus
 * those calendar_dates adds on that date, minus those it removes. [calendars] and
 * [calendarDates] may contain rows for other dates; they are ignored.
 */
fun activeServiceIds(
    date: LocalDate,
    calendars: List<ServiceCalendarEntity>,
    calendarDates: List<CalendarDateEntity>
): Set<String> {
    val exceptions = calendarDates.filter { it.date == date }
    val scheduled = calendars
        .filter { date in it.startDate..it.endDate && it.runsOn(date.dayOfWeek) }
        .map { it.serviceId }
    val added = exceptions.filter { it.added }.map { it.serviceId }
    val removed = exceptions.filterNot { it.added }.map { it.serviceId }.toSet()
    return (scheduled + added).toSet() - removed
}

/**
 * The instant of a GTFS time on [serviceDate]. GTFS measures times from "noon minus 12 hours",
 * which is midnight except on daylight-saving days: [seconds] past 24 hours land on the next
 * calendar day, and on the day clocks fall back 00:00:00 is 1:00 a.m. local time.
 */
fun serviceTime(serviceDate: LocalDate, seconds: Int, zone: ZoneId = PRT_TIME_ZONE): Instant =
    serviceDate
        .atTime(LocalTime.NOON)
        .atZone(zone)
        .minusHours(12)
        .toInstant()
        .plusSeconds(seconds.toLong())

private fun ServiceCalendarEntity.runsOn(day: DayOfWeek): Boolean = when (day) {
    DayOfWeek.MONDAY -> monday
    DayOfWeek.TUESDAY -> tuesday
    DayOfWeek.WEDNESDAY -> wednesday
    DayOfWeek.THURSDAY -> thursday
    DayOfWeek.FRIDAY -> friday
    DayOfWeek.SATURDAY -> saturday
    DayOfWeek.SUNDAY -> sunday
}
