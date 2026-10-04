package org.openprt.app.planner

import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.EARTH_RADIUS_METERS
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.haversineMeters

/** Farthest straight-line walk between two stops that the planner treats as a transfer. */
const val DEFAULT_MAX_TRANSFER_WALK_METERS = 400.0

/**
 * A place where riders board or get off. [stopId] is the GTFS stop_id the timetable uses;
 * [trueTimeStopId] is the ID TrueTime knows the stop by, for live predictions.
 */
data class TransitStop(
    val stopId: String,
    val name: String,
    val location: LatLng,
    val trueTimeStopId: String = stopId
)

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
internal class Pattern(val routeId: String, val stops: IntArray, trips: List<ScheduledTrip>) {
    val trips: List<ScheduledTrip> = trips.sortedBy { it.stops.first().departureSeconds }

    /**
     * The first trip riders can board at [position] at or after [seconds], or null. Relies on the
     * no-overtaking assumption: the earliest trip at the first stop is the earliest everywhere.
     */
    fun earliestTrip(position: Int, seconds: Int): ScheduledTrip? = trips.firstOrNull {
        val stop = it.stops[position]
        stop.pickupAllowed && stop.departureSeconds >= seconds
    }

    /** See [TransitNetwork.mirrored]. */
    fun mirrored() = Pattern(routeId, stops.reversedArray(), trips.map { it.mirrored() })
}

private fun ScheduledTrip.mirrored() = copy(
    stops = stops.reversed().map {
        TripStop(
            stopId = it.stopId,
            arrivalSeconds = -it.departureSeconds,
            departureSeconds = -it.arrivalSeconds,
            pickupAllowed = it.dropOffAllowed,
            dropOffAllowed = it.pickupAllowed
        )
    }
)

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
class TransitNetwork private constructor(
    internal val stops: List<TransitStop>,
    internal val patterns: List<Pattern>,
    /** For each stop, the other stops within transfer walking distance. */
    internal val transfers: List<List<NearStop>>
) {
    constructor(
        stops: List<TransitStop>,
        trips: List<ScheduledTrip>,
        maxTransferWalkMeters: Double = DEFAULT_MAX_TRANSFER_WALK_METERS
    ) : this(stops.toList(), trips, maxTransferWalkMeters, stopIndexOf(stops))

    private constructor(
        stops: List<TransitStop>,
        trips: List<ScheduledTrip>,
        maxTransferWalkMeters: Double,
        stopIndex: Map<String, Int>
    ) : this(
        stops,
        buildPatterns(trips, stopIndex),
        buildTransfers(stops, maxTransferWalkMeters)
    )

    internal val patternsAtStop: List<List<PatternVisit>> =
        List(stops.size) { mutableListOf<PatternVisit>() }.also { visits ->
            patterns.forEachIndexed { p, pattern ->
                pattern.stops.forEachIndexed { position, stop ->
                    visits[stop] += PatternVisit(p, position)
                }
            }
        }

    /**
     * This network with time running backwards: every trip is reversed and its times negated,
     * boarding and getting off swap, and transfers stay as they are since walks go both ways.
     * The earliest arrival in it is the latest departure here, so [RoutePlanner] plans "arrive
     * by" trips with its usual forward search. Built on first use and kept, like the network.
     */
    internal val mirrored: TransitNetwork by lazy {
        TransitNetwork(stops, patterns.map { it.mirrored() }, transfers)
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
}

private fun stopIndexOf(stops: List<TransitStop>): Map<String, Int> =
    stops.withIndex().associate { (index, stop) -> stop.stopId to index }.also {
        require(it.size == stops.size) { "stop IDs must be unique" }
    }

private fun buildPatterns(trips: List<ScheduledTrip>, stopIndex: Map<String, Int>): List<Pattern> =
    trips
        .groupBy { trip ->
            require(trip.stops.size >= 2) { "trip ${trip.tripId} has fewer than two stops" }
            trip.routeId to trip.stops.map { stop ->
                requireNotNull(stopIndex[stop.stopId]) {
                    "trip ${trip.tripId} visits unknown stop ${stop.stopId}"
                }
            }
        }
        .map { (key, patternTrips) -> Pattern(key.first, key.second.toIntArray(), patternTrips) }

// Sweeps stops in latitude order so each stop is only compared with stops in its latitude
// band, instead of with every other stop.
private fun buildTransfers(stops: List<TransitStop>, radiusMeters: Double): List<List<NearStop>> {
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
