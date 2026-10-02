package org.openprt.app.planner

import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.EARTH_RADIUS_METERS
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.haversineMeters

/** Farthest straight-line walk between two stops that the planner treats as a transfer. */
const val DEFAULT_MAX_TRANSFER_WALK_METERS = 400.0

/** A place where riders board or get off. */
data class TransitStop(val stopId: String, val name: String, val location: LatLng)

/**
 * One scheduled stop of a trip. Times are seconds after the start of the service day and can
 * pass 24 hours, as in GTFS stop_times.txt.
 */
data class TripStop(
    val stopId: String,
    val arrivalSeconds: Int,
    val departureSeconds: Int,
    val pickupAllowed: Boolean = true,
    val dropOffAllowed: Boolean = true
)

/** One run of a vehicle; [stops] are in stop_sequence order. */
data class ScheduledTrip(
    val tripId: String,
    val routeId: String,
    val headsign: String?,
    val stops: List<TripStop>
)

/** A stop index into [TransitNetwork.stops] and its straight-line distance from some point. */
internal data class NearStop(val stop: Int, val distanceMeters: Double)

/**
 * Trips of one route that visit the same stops in the same order, earliest first. RAPTOR scans a
 * pattern as a unit, so it assumes trips of a pattern never overtake each other.
 */
internal class Pattern(val routeId: String, val stops: IntArray, val trips: List<ScheduledTrip>) {
    /**
     * The first trip riders can board at [position] at or after [seconds], or null. Relies on the
     * no-overtaking assumption: the earliest trip at the first stop is the earliest everywhere.
     */
    fun earliestTrip(position: Int, seconds: Int): ScheduledTrip? = trips.firstOrNull {
        val stop = it.stops[position]
        stop.pickupAllowed && stop.departureSeconds >= seconds
    }
}

/** Where a pattern visits a stop: [position] in [Pattern.stops] of `patterns[pattern]`. */
internal data class PatternVisit(val pattern: Int, val position: Int)

/**
 * The timetable of one service day, indexed for [RoutePlanner]. Pure Kotlin so it can move to a
 * shared module for iOS.
 *
 * Building it groups trips into patterns and precomputes transfer walks between stops within
 * [maxTransferWalkMeters] of each other, so build it once per service day and reuse it.
 *
 * @throws IllegalArgumentException if a trip has fewer than two stops or visits a stop missing
 *   from [stops], or if two stops share an ID.
 */
class TransitNetwork(
    stops: List<TransitStop>,
    trips: List<ScheduledTrip>,
    maxTransferWalkMeters: Double = DEFAULT_MAX_TRANSFER_WALK_METERS
) {
    internal val stops: List<TransitStop> = stops.toList()
    private val stopIndex: Map<String, Int> =
        stops.withIndex().associate { (index, stop) -> stop.stopId to index }

    internal val patterns: List<Pattern>
    internal val patternsAtStop: List<List<PatternVisit>>

    /** For each stop, the other stops within transfer walking distance. */
    internal val transfers: List<List<NearStop>> = buildTransfers(maxTransferWalkMeters)

    init {
        require(stopIndex.size == stops.size) { "stop IDs must be unique" }
        patterns = buildPatterns(trips)
        val visits = List(stops.size) { mutableListOf<PatternVisit>() }
        patterns.forEachIndexed { p, pattern ->
            pattern.stops.forEachIndexed { position, stop ->
                visits[stop] += PatternVisit(p, position)
            }
        }
        patternsAtStop = visits
    }

    /** Stops within [radiusMeters] of [point], nearest first (ties by stop order). */
    internal fun stopsWithin(point: LatLng, radiusMeters: Double): List<NearStop> {
        val box = BoundingBox.around(point, radiusMeters)
        return stops.indices
            .filter { stops[it].location in box }
            .map { NearStop(it, haversineMeters(point, stops[it].location)) }
            .filter { it.distanceMeters <= radiusMeters }
            .sortedWith(compareBy({ it.distanceMeters }, { it.stop }))
    }

    private fun buildPatterns(trips: List<ScheduledTrip>): List<Pattern> = trips
        .groupBy { trip ->
            require(trip.stops.size >= 2) { "trip ${trip.tripId} has fewer than two stops" }
            trip.routeId to trip.stops.map { stop ->
                requireNotNull(stopIndex[stop.stopId]) {
                    "trip ${trip.tripId} visits unknown stop ${stop.stopId}"
                }
            }
        }
        .map { (key, patternTrips) ->
            Pattern(
                routeId = key.first,
                stops = key.second.toIntArray(),
                trips = patternTrips.sortedBy { it.stops.first().departureSeconds }
            )
        }

    // Sweeps stops in latitude order so each stop is only compared with stops in its latitude
    // band, instead of with every other stop.
    private fun buildTransfers(radiusMeters: Double): List<List<NearStop>> {
        val result = List(stops.size) { mutableListOf<NearStop>() }
        val byLatitude = stops.indices.sortedBy { stops[it].location.latitude }
        val bandDegrees = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS)
        for ((i, from) in byLatitude.withIndex()) {
            val fromLocation = stops[from].location
            for (to in byLatitude.subList(i + 1, byLatitude.size)) {
                val toLocation = stops[to].location
                if (toLocation.latitude - fromLocation.latitude > bandDegrees) break
                val distance = haversineMeters(fromLocation, toLocation)
                if (distance <= radiusMeters) {
                    result[from] += NearStop(to, distance)
                    result[to] += NearStop(from, distance)
                }
            }
        }
        return result
    }
}
