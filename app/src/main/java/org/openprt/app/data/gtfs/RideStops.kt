package org.openprt.app.data.gtfs

import org.openprt.app.geo.LatLng

/** Where the stops a ride passes come from; an interface so screens can be tested with a fake. */
fun interface RideStopsSource {
    /**
     * Positions of the stops [tripId] serves from [fromStopId] to [toStopId], both included, in
     * travel order; empty when the trip or either stop is not in the timetable.
     */
    suspend fun stopsBetween(tripId: String, fromStopId: String, toStopId: String): List<LatLng>
}

/** Reads ride stops from the imported timetable. */
class RoomRideStopsSource(private val dao: GtfsDao) : RideStopsSource {
    override suspend fun stopsBetween(
        tripId: String,
        fromStopId: String,
        toStopId: String
    ): List<LatLng> = dao.getStopsOfTrip(tripId)
        .sliceBetween(fromStopId, toStopId) { it.stopId }
        .map { LatLng(it.latitude, it.longitude) }
}

/**
 * The part of this travel-ordered list from the first item with key [from] to the first item
 * with key [to] after it, both included; empty when either is missing. A loop trip that passes
 * [to] before [from] is ridden to its later visit.
 */
internal fun <T> List<T>.sliceBetween(from: String, to: String, key: (T) -> String): List<T> {
    val start = indexOfFirst { key(it) == from }
    if (start < 0) return emptyList()
    val end = (start + 1 until size).firstOrNull { key(this[it]) == to } ?: return emptyList()
    return subList(start, end + 1)
}
