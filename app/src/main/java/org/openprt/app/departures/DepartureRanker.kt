package org.openprt.app.departures

import java.time.Clock
import java.time.Duration
import kotlin.math.ceil
import org.openprt.app.data.truetime.Prediction

/** Average adult walking pace used when the caller does not supply one. */
const val DEFAULT_WALKING_SPEED_METERS_PER_SECOND = 1.2

/**
 * A stop the user could walk to. [stopId] must use the same ID scheme as
 * [Prediction.stopId], because predictions are matched to stops by plain string equality.
 */
data class WalkableStop(val stopId: String, val distanceMeters: Double)

/**
 * A bus the user can catch: [prediction] at [stop], reached after [walkTime].
 * [timeUntilDeparture] is measured from now to the predicted time at the stop, and
 * [spareTime] is how long the user would wait at the stop after walking there.
 */
data class RankedDeparture(
    val prediction: Prediction,
    val stop: WalkableStop,
    val walkTime: Duration,
    val timeUntilDeparture: Duration,
    val spareTime: Duration
)

/**
 * Picks the most suitable departures near the user: buses they can still walk to in time,
 * earliest first, keeping only the best stop for each route and direction.
 *
 * "Now" always comes from [clock], so results depend only on the inputs.
 *
 * @throws IllegalArgumentException if [walkingSpeedMetersPerSecond] is not positive.
 */
class DepartureRanker(
    private val clock: Clock,
    private val walkingSpeedMetersPerSecond: Double = DEFAULT_WALKING_SPEED_METERS_PER_SECOND
) {
    init {
        require(walkingSpeedMetersPerSecond > 0) {
            "walkingSpeedMetersPerSecond must be positive: $walkingSpeedMetersPerSecond"
        }
    }

    /** Time to walk [distanceMeters] in a straight line, rounded up to the next whole second. */
    fun walkTime(distanceMeters: Double): Duration =
        Duration.ofSeconds(ceil(distanceMeters / walkingSpeedMetersPerSecond).toLong())

    /**
     * Catchable departures from [predictions] at [stops], ordered by predicted time, then by
     * shorter walk, route, direction and stop ID so equal inputs always give the same order.
     *
     * A bus is catchable when the user reaches the stop no later than its predicted time.
     * Predictions at stops not in [stops] are ignored. When a route and direction is served at
     * several stops, only the departure that can be boarded earliest is kept (shorter walk wins
     * a tie).
     */
    fun rank(stops: List<WalkableStop>, predictions: List<Prediction>): List<RankedDeparture> {
        val now = clock.instant()
        val stopsById = stops.associateBy { it.stopId }
        return predictions
            .mapNotNull { prediction ->
                val stop = stopsById[prediction.stopId] ?: return@mapNotNull null
                val walk = walkTime(stop.distanceMeters)
                val untilDeparture = Duration.between(now, prediction.predictedTime)
                val spare = untilDeparture - walk
                if (spare.isNegative) return@mapNotNull null
                RankedDeparture(prediction, stop, walk, untilDeparture, spare)
            }
            .sortedWith(ORDER)
            // distinctBy keeps the first per key, which after sorting is the earliest boarding.
            .distinctBy { it.prediction.route to it.prediction.routeDirection }
    }

    private companion object {
        val ORDER = compareBy<RankedDeparture>(
            { it.prediction.predictedTime },
            { it.walkTime },
            { it.prediction.route },
            { it.prediction.routeDirection },
            { it.stop.stopId }
        )
    }
}
