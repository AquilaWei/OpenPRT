package org.openprt.app.data.gtfs

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.ScheduledTrip
import org.openprt.app.planner.TransitNetwork
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.TripStop

/** Networks of some service days, all read from the import with id [importId]. */
class TimetableNetworks(val importId: Long, val networks: Map<LocalDate, TransitNetwork>)

/** Where the planner gets its timetable; an interface so tests can count network builds. */
fun interface TransitNetworkSource {
    /**
     * Reads one version of the imported timetable: its import id, then the networks of the days
     * [daysToBuild] picks for that id, so a caller already holding networks of that import builds
     * only the days it lacks. Null when no GTFS data has been imported yet. A day with no service
     * gives a network without trips, not null.
     */
    suspend fun networks(daysToBuild: (importId: Long) -> List<LocalDate>): TimetableNetworks?
}

/**
 * [TransitNetworkSource] over the imported GTFS timetable in [database]. The reads of one call
 * share a transaction, so a background import committing meanwhile cannot mix old and new rows;
 * that import waits for them, and they wait for an import already running. Each day's network
 * is then built on [dispatcher], which takes a noticeable fraction of a second for PRT's feed, so
 * callers should keep the result per day and import id.
 */
class RoomTransitNetworkSource(
    private val database: GtfsDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Tests wrap the DAO to act between its reads.
    private val dao: GtfsDao = database.gtfsDao()
) : TransitNetworkSource {
    override suspend fun networks(
        daysToBuild: (importId: Long) -> List<LocalDate>
    ): TimetableNetworks? {
        val (importId, rows) = database.withTransaction {
            val importId = dao.getImportId() ?: return@withTransaction null
            importId to daysToBuild(importId).associateWith { readServiceDay(it) }
        } ?: return null
        val networks = withContext(dispatcher) {
            rows.mapValues { (_, day) -> buildNetwork(day.stops, day.trips, day.stopTimes) }
        }
        return TimetableNetworks(importId, networks)
    }

    private suspend fun readServiceDay(serviceDate: LocalDate): ServiceDayRows {
        val serviceIds = activeServiceIds(
            serviceDate,
            dao.getCalendarsCovering(serviceDate),
            dao.getCalendarDatesOn(serviceDate)
        )
        return ServiceDayRows(
            dao.getAllStops(),
            dao.getTripsOfServices(serviceIds),
            dao.getStopTimesOfServices(serviceIds)
        )
    }

    private class ServiceDayRows(
        val stops: List<StopEntity>,
        val trips: List<TripEntity>,
        val stopTimes: List<StopTimeEntity>
    )
}

/**
 * The network of [stops] and [trips], whose [stopTimes] are grouped by trip and in travel order.
 * Trips the planner could not use are left out rather than failing the whole day: those with
 * fewer than two stop times, and those visiting a stop missing from stops.txt.
 */
internal fun buildNetwork(
    stops: List<StopEntity>,
    trips: List<TripEntity>,
    stopTimes: List<StopTimeEntity>
): TransitNetwork {
    val transitStops = stops.map {
        TransitStop(it.stopId, it.name, LatLng(it.latitude, it.longitude), it.trueTimeStopId)
    }
    val knownStopIds = stops.mapTo(HashSet()) { it.stopId }
    val stopTimesOfTrip = stopTimes.groupBy { it.tripId }
    val scheduledTrips = trips.mapNotNull { trip ->
        val tripStopTimes = stopTimesOfTrip[trip.tripId].orEmpty()
        if (tripStopTimes.size < 2 || tripStopTimes.any { it.stopId !in knownStopIds }) {
            return@mapNotNull null
        }
        ScheduledTrip(
            tripId = trip.tripId,
            routeId = trip.routeId,
            headsign = trip.headsign,
            stops = tripStopTimes.map {
                TripStop(
                    stopId = it.stopId,
                    arrivalSeconds = it.arrivalSeconds,
                    departureSeconds = it.departureSeconds,
                    pickupAllowed = it.pickupAllowed,
                    dropOffAllowed = it.dropOffAllowed
                )
            }
        )
    }
    return TransitNetwork(transitStops, scheduledTrips)
}
