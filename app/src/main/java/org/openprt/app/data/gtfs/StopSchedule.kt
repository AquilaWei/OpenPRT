package org.openprt.app.data.gtfs

import java.time.Duration
import java.time.Instant

/** A timetabled departure from a stop, named the way riders see it. */
data class StopScheduleEntry(
    /** The route's short name, e.g. "61C"; the GTFS route ID when the feed has none. */
    val route: String,
    val headsign: String?,
    val time: Instant
)

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
 * asks yesterday's, whose trips can still run after midnight (times past 24:00:00).
 */
class RoomStopScheduleSource(
    private val dao: GtfsDao,
    private val timetable: GtfsTimetable = GtfsTimetable(dao)
) : StopScheduleSource {
    override suspend fun departures(
        trueTimeStopId: String,
        after: Instant,
        limit: Int
    ): List<StopScheduleEntry> {
        val today = after.atZone(PRT_TIME_ZONE).toLocalDate()
        val scheduled = dao.getStopsByTrueTimeId(trueTimeStopId).flatMap { stop ->
            listOf(today.minusDays(1), today).flatMap { serviceDate ->
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
                time = it.departureTime
            )
        }
    }
}
