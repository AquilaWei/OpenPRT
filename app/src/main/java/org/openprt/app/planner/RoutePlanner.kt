package org.openprt.app.planner

import kotlin.math.ceil
import org.openprt.app.departures.DEFAULT_WALKING_SPEED_METERS_PER_SECOND
import org.openprt.app.geo.LatLng

/** Farthest straight-line walk from the origin to the first stop, or from the last stop on. */
const val DEFAULT_MAX_ACCESS_WALK_METERS = 800.0

/** Most rides in one itinerary; also the most itineraries returned, one per ride count. */
const val DEFAULT_MAX_RIDES = 3

/**
 * Plans transit trips on a [TransitNetwork] with RAPTOR (Delling, Pajor & Werneck, 2012), on the
 * device and without network access.
 *
 * Round k finds the earliest arrival at every stop using at most k rides. An itinerary is kept
 * only when it reaches the destination strictly earlier than every itinerary with fewer rides,
 * so the result is the Pareto set over arrival time and number of rides, and of two plans that
 * arrive together the one with fewer transfers wins.
 *
 * Walks are straight lines at [walkingSpeedMetersPerSecond], rounded up to whole seconds.
 * Transfers need no buffer: a bus can be boarded at the second the previous one arrives.
 *
 * @throws IllegalArgumentException if [walkingSpeedMetersPerSecond] or [maxRides] is not positive.
 */
class RoutePlanner(
    private val network: TransitNetwork,
    private val walkingSpeedMetersPerSecond: Double = DEFAULT_WALKING_SPEED_METERS_PER_SECOND,
    private val maxAccessWalkMeters: Double = DEFAULT_MAX_ACCESS_WALK_METERS,
    private val maxRides: Int = DEFAULT_MAX_RIDES
) {
    init {
        require(walkingSpeedMetersPerSecond > 0) {
            "walkingSpeedMetersPerSecond must be positive: $walkingSpeedMetersPerSecond"
        }
        require(maxRides > 0) { "maxRides must be positive: $maxRides" }
    }

    /**
     * Itineraries from [origin] to [destination] leaving no earlier than [departureSeconds]
     * (seconds after the start of the network's service day). Never plans a walk-only trip.
     */
    fun plan(origin: LatLng, destination: LatLng, departureSeconds: Int): PlanResult {
        val access = network.stopsWithin(origin, maxAccessWalkMeters)
        if (access.isEmpty()) return PlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_ORIGIN)
        val egress = network.stopsWithin(destination, maxAccessWalkMeters)
        if (egress.isEmpty()) return PlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION)
        val itineraries = RaptorSearch(departureSeconds, access, egress).run()
        return if (itineraries.isEmpty()) {
            PlanResult.NoRoute(NoRouteReason.NO_CONNECTION)
        } else {
            PlanResult.Found(itineraries)
        }
    }

    private fun walkSeconds(distanceMeters: Double): Int =
        ceil(distanceMeters / walkingSpeedMetersPerSecond).toInt()

    /** How a stop was reached in a round. */
    private sealed interface Label {
        data class Access(val distanceMeters: Double) : Label

        class Ride(val pattern: Pattern, val trip: ScheduledTrip, val board: Int, val alight: Int) :
            Label {
            val arrivalSeconds: Int get() = trip.stops[alight].arrivalSeconds
        }

        /** Walked from [from], which was reached by a ride in the same round. */
        data class Transfer(val from: Int, val distanceMeters: Double) : Label
    }

    /** One search; holds the per-round state, so it is used once and thrown away. */
    private inner class RaptorSearch(
        private val departureSeconds: Int,
        private val access: List<NearStop>,
        private val egress: List<NearStop>
    ) {
        private val stopCount = network.stops.size

        // arrival[k][s]: earliest arrival at s with at most k rides; round 0 is the walk from the
        // origin. labels[k][s] says how round k improved s and is null if it did not.
        private val arrival = Array(maxRides + 1) { IntArray(stopCount) { UNREACHED } }
        private val labels = Array(maxRides + 1) { arrayOfNulls<Label>(stopCount) }

        // Kept apart from labels because a transfer walk can later replace a stop's label, and
        // walks from that stop still need the ride that got there.
        private val rides = Array(maxRides + 1) { arrayOfNulls<Label.Ride>(stopCount) }
        private val best = IntArray(stopCount) { UNREACHED }
        private var bestAtDestination = UNREACHED
        private val found = mutableListOf<Itinerary>()

        fun run(): List<Itinerary> {
            var marked = walkFromOrigin()
            for (round in 1..maxRides) {
                if (marked.isEmpty()) break
                arrival[round - 1].copyInto(arrival[round])
                val reachedByRide = scanPatterns(round, marked)
                recordDestination(round)
                marked = walkTransfers(round, reachedByRide)
            }
            return found
        }

        private fun improve(round: Int, stop: Int, seconds: Int, label: Label): Boolean {
            if (seconds >= minOf(best[stop], bestAtDestination)) return false
            arrival[round][stop] = seconds
            best[stop] = seconds
            labels[round][stop] = label
            return true
        }

        private fun walkFromOrigin(): Set<Int> = access
            .filter {
                improve(
                    0,
                    it.stop,
                    departureSeconds + walkSeconds(it.distanceMeters),
                    Label.Access(it.distanceMeters)
                )
            }
            .mapTo(LinkedHashSet()) { it.stop }

        private fun scanPatterns(round: Int, marked: Set<Int>): Set<Int> {
            // Each pattern is scanned once, from the earliest marked stop on it.
            val firstPosition = IntArray(network.patterns.size) { NOT_QUEUED }
            for (stop in marked) {
                for (visit in network.patternsAtStop[stop]) {
                    val queued = firstPosition[visit.pattern]
                    if (queued == NOT_QUEUED || visit.position < queued) {
                        firstPosition[visit.pattern] = visit.position
                    }
                }
            }
            val reached = LinkedHashSet<Int>()
            firstPosition.forEachIndexed { p, start ->
                if (start != NOT_QUEUED) scanPattern(round, network.patterns[p], start, reached)
            }
            return reached
        }

        private fun scanPattern(
            round: Int,
            pattern: Pattern,
            start: Int,
            reached: MutableSet<Int>
        ) {
            var trip: ScheduledTrip? = null
            var board = 0
            for (position in start until pattern.stops.size) {
                val stop = pattern.stops[position]
                val onBoard = trip
                if (onBoard != null && onBoard.stops[position].dropOffAllowed) {
                    val ride = Label.Ride(pattern, onBoard, board, position)
                    if (improve(round, stop, ride.arrivalSeconds, ride)) {
                        rides[round][stop] = ride
                        reached += stop
                    }
                }
                // Board here if the previous round got here in time for an earlier trip.
                val ready = arrival[round - 1][stop]
                if (ready == UNREACHED) continue
                val candidate = pattern.earliestTrip(position, ready) ?: continue
                if (onBoard == null ||
                    candidate.stops[position].departureSeconds <
                    onBoard.stops[position].departureSeconds
                ) {
                    trip = candidate
                    board = position
                }
            }
        }

        // Only stops reached by a ride in this round count, so a plan never ends with two walks.
        private fun recordDestination(round: Int) {
            var bestEgress: NearStop? = null
            for (candidate in egress) {
                val ride = rides[round][candidate.stop] ?: continue
                val seconds = ride.arrivalSeconds + walkSeconds(candidate.distanceMeters)
                if (seconds < bestAtDestination) {
                    bestAtDestination = seconds
                    bestEgress = candidate
                }
            }
            if (bestEgress != null) found += itinerary(round, bestEgress)
        }

        private fun walkTransfers(round: Int, reachedByRide: Set<Int>): Set<Int> {
            val marked = LinkedHashSet(reachedByRide)
            for (from in reachedByRide) {
                val arrived = checkNotNull(rides[round][from]).arrivalSeconds
                for (to in network.transfers[from]) {
                    val seconds = arrived + walkSeconds(to.distanceMeters)
                    val label = Label.Transfer(from, to.distanceMeters)
                    if (improve(round, to.stop, seconds, label)) marked += to.stop
                }
            }
            return marked
        }

        /** Follows the labels back from the last ride, which ends at [egress] in [round]. */
        private fun itinerary(round: Int, egress: NearStop): Itinerary {
            var ride = checkNotNull(rides[round][egress.stop])
            val lastStop = network.stops[egress.stop]
            val legs = ArrayDeque<Leg>()
            legs.addFirst(walk(lastStop, null, egress.distanceMeters, ride.arrivalSeconds))
            var k = round
            while (true) {
                legs.addFirst(rideLeg(ride))
                val boardStop = ride.pattern.stops[ride.board]
                val (labelRound, label) = latestLabel(boardStop, k - 1)
                when (label) {
                    is Label.Access -> {
                        val leaves = ride.trip.stops[ride.board].departureSeconds
                        val walk = walkSeconds(label.distanceMeters)
                        legs.addFirst(
                            WalkLeg(
                                null,
                                network.stops[boardStop],
                                label.distanceMeters,
                                leaves - walk,
                                leaves
                            )
                        )
                        return Itinerary(legs.toList())
                    }

                    is Label.Transfer -> {
                        val previous = checkNotNull(rides[labelRound][label.from])
                        legs.addFirst(
                            walk(
                                network.stops[label.from],
                                network.stops[boardStop],
                                label.distanceMeters,
                                previous.arrivalSeconds
                            )
                        )
                        ride = previous
                    }

                    is Label.Ride -> ride = label
                }
                k = labelRound
            }
        }

        // The label that set arrival[upToRound][stop]: the latest round at or before it that
        // improved the stop.
        private fun latestLabel(stop: Int, upToRound: Int): Pair<Int, Label> {
            for (k in upToRound downTo 0) {
                labels[k][stop]?.let { return k to it }
            }
            error("stop $stop was boarded at without being reached")
        }

        private fun walk(
            from: TransitStop?,
            to: TransitStop?,
            distanceMeters: Double,
            startSeconds: Int
        ) = WalkLeg(
            from,
            to,
            distanceMeters,
            startSeconds,
            startSeconds + walkSeconds(distanceMeters)
        )

        private fun rideLeg(ride: Label.Ride) = RideLeg(
            tripId = ride.trip.tripId,
            routeId = ride.trip.routeId,
            headsign = ride.trip.headsign,
            from = network.stops[ride.pattern.stops[ride.board]],
            to = network.stops[ride.pattern.stops[ride.alight]],
            startSeconds = ride.trip.stops[ride.board].departureSeconds,
            endSeconds = ride.arrivalSeconds
        )
    }

    private companion object {
        const val UNREACHED = Int.MAX_VALUE
        const val NOT_QUEUED = -1
    }
}
