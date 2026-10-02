package org.openprt.app.planner

/**
 * One part of a trip plan. Times are seconds after the start of the service day the
 * [TransitNetwork] was built for, like GTFS times; they can pass 24 hours.
 */
sealed interface Leg {
    val startSeconds: Int
    val endSeconds: Int
}

/**
 * A straight-line walk. [from] is null when the walk starts at the trip's origin, [to] is null
 * when it ends at the destination.
 */
data class WalkLeg(
    val from: TransitStop?,
    val to: TransitStop?,
    val distanceMeters: Double,
    override val startSeconds: Int,
    override val endSeconds: Int
) : Leg

/** A ride on [tripId] from boarding at [from] to getting off at [to]. */
data class RideLeg(
    val tripId: String,
    val routeId: String,
    val headsign: String?,
    val from: TransitStop,
    val to: TransitStop,
    override val startSeconds: Int,
    override val endSeconds: Int
) : Leg

/**
 * A way from the origin to the destination: walk, then one or more rides with walks or waits in
 * between, then walk. The first walk ends as the first bus leaves, so [departureSeconds] is
 * when the rider has to set off, which may be later than the time asked for.
 */
data class Itinerary(val legs: List<Leg>) {
    val departureSeconds: Int get() = legs.first().startSeconds
    val arrivalSeconds: Int get() = legs.last().endSeconds
    val rides: List<RideLeg> get() = legs.filterIsInstance<RideLeg>()
    val transfers: Int get() = rides.size - 1
}

/** Why [RoutePlanner] found no itinerary. */
enum class NoRouteReason {
    /** No stop is within walking distance of the origin. */
    NO_STOP_NEAR_ORIGIN,

    /** No stop is within walking distance of the destination. */
    NO_STOP_NEAR_DESTINATION,

    /** Stops are near both ends, but no trips connect them that day within the ride limit. */
    NO_CONNECTION
}

sealed interface PlanResult {
    /** At least one itinerary, fewest rides first; each later one arrives strictly earlier. */
    data class Found(val itineraries: List<Itinerary>) : PlanResult

    data class NoRoute(val reason: NoRouteReason) : PlanResult
}
