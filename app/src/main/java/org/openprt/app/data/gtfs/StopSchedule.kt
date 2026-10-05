package org.openprt.app.data.gtfs

import androidx.room.withTransaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** A timetabled departure from a stop, named the way riders see it. */
data class StopScheduleEntry(
    /** The route's short name, e.g. "61C"; the GTFS route ID when the feed has none. */
    val route: String,
    val headsign: String?,
    val time: Instant,
    /** Which run this is, so [ScheduledTripSource] can list where it goes from here. */
    val run: ScheduledRun
)

/**
 * One timetabled run calling at a stop: trip [tripId] of [serviceDate], at its stop number
 * [stopSequence]. The service date matters for trips that run past midnight.
 */
data class ScheduledRun(val tripId: String, val serviceDate: LocalDate, val stopSequence: Int)

/** A stop a timetabled run calls at and when it is due there. */
data class ScheduledStopTime(val stopName: String, val time: Instant)

/** The rest of a timetabled run; an interface so the stop panel can be tested with a fake. */
fun interface ScheduledTripSource {
    /**
     * The stops of [run]'s trip from its stop on, in travel order, the first one at its departure
     * time and the rest at their arrival times. Empty when the trip is no longer in the timetable,
     * for instance after an update replaced it.
     */
    suspend fun stopsFrom(run: ScheduledRun): List<ScheduledStopTime>
}

/** [ScheduledTripSource] over the imported GTFS timetable. */
class RoomScheduledTripSource(private val dao: GtfsDao) : ScheduledTripSource {
    override suspend fun stopsFrom(run: ScheduledRun): List<ScheduledStopTime> =
        dao.getTripStopTimesFrom(run.tripId, run.stopSequence).mapIndexed { index, row ->
            val seconds = if (index == 0) row.departureSeconds else row.arrivalSeconds
            ScheduledStopTime(row.stopName, serviceTime(run.serviceDate, seconds))
        }
}

/** Timetabled departures from a stop; an interface so the stop panel can be tested with a fake. */
fun interface StopScheduleSource {
    /**
     * Up to [limit] departures from the stop TrueTime calls [trueTimeStopId] at or after
     * [after], earliest first. Empty when the stop is unknown or the timetable is not imported.
     */
    suspend fun departures(
        trueTimeStopId: String,
        after: Instant,
        limit: Int
    ): List<StopScheduleEntry>
}

/**
 * [StopScheduleSource] over the imported GTFS timetable. Besides today's service day it also
 * asks yesterday's, whose trips can still run after midnight (times past 24:00:00), and
 * tomorrow's, so the list is not empty once today's last bus has left.
 *
 * Every read of one call shares a transaction in [database], so a background import committing
 * meanwhile cannot pair one feed's stops and calendars with another's trips and routes.
 */
class RoomStopScheduleSource(
    private val database: GtfsDatabase,
    // Tests wrap the DAO to act between its reads.
    private val dao: GtfsDao = database.gtfsDao(),
    private val timetable: GtfsTimetable = GtfsTimetable(database, dao)
) : StopScheduleSource {
    override suspend fun departures(
        trueTimeStopId: String,
        after: Instant,
        limit: Int
    ): List<StopScheduleEntry> = database.withTransaction {
        readDepartures(trueTimeStopId, after, limit)
    }

    private suspend fun readDepartures(
        trueTimeStopId: String,
        after: Instant,
        limit: Int
    ): List<StopScheduleEntry> {
        val today = after.atZone(PRT_TIME_ZONE).toLocalDate()
        val scheduled = dao.getStopsByTrueTimeId(trueTimeStopId).flatMap { stop ->
            listOf(today.minusDays(1), today, today.plusDays(1)).flatMap { serviceDate ->
                val seconds = Duration.between(serviceTime(serviceDate, 0), after).seconds
                timetable.departuresAfter(stop.stopId, serviceDate, seconds.toInt(), limit)
            }
        }
            .sortedWith(compareBy({ it.departureTime }, { it.routeId }, { it.tripId }))
            .take(limit)
        if (scheduled.isEmpty()) return emptyList()
        val routeNames = dao.getRoutes(scheduled.map { it.routeId }.toSet())
            .associate { it.routeId to (it.shortName ?: it.routeId) }
        return scheduled.map {
            StopScheduleEntry(
                route = routeNames[it.routeId] ?: it.routeId,
                headsign = it.headsign,
                time = it.departureTime,
                run = ScheduledRun(it.tripId, it.serviceDate, it.stopSequence)
            )
        }
    }
}
