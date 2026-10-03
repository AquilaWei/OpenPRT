package org.openprt.app.data.gtfs

import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.ScheduledTrip
import org.openprt.app.planner.TransitNetwork
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.TripStop

/** Where the planner gets its timetable; an interface so tests can count network builds. */
fun interface TransitNetworkSource {
    /**
     * The network of everything scheduled on [serviceDate], or null when no GTFS data has been
     * imported yet. A day with no service gives a network without trips, not null.
     */
    suspend fun network(serviceDate: LocalDate): TransitNetwork?
}

/**
 * [TransitNetworkSource] over the imported GTFS timetable. Each call reads a whole service day
 * from Room and builds the network on [dispatcher], which takes a noticeable fraction of a second
 * for PRT's feed, so callers should cache the result per day.
 */
class RoomTransitNetworkSource(
    private val dao: GtfsDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TransitNetworkSource {
    override suspend fun network(serviceDate: LocalDate): TransitNetwork? {
        // The importer fills every table in one transaction, so no stops means no timetable.
        if (dao.countStops() == 0) return null
        val serviceIds = activeServiceIds(
            serviceDate,
            dao.getCalendarsCovering(serviceDate),
            dao.getCalendarDatesOn(serviceDate)
        )
        val stops = dao.getAllStops()
        val trips = dao.getTripsOfServices(serviceIds)
        val stopTimes = dao.getStopTimesOfServices(serviceIds)
        return withContext(dispatcher) { buildNetwork(stops, trips, stopTimes) }
    }
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
