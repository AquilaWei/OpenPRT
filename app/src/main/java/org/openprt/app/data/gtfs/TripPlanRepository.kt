package org.openprt.app.data.gtfs

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.PlanResult
import org.openprt.app.planner.RoutePlanner

/**
 * An [itinerary] on the timetable of [serviceDate]. Its times are seconds after the start of that
 * service day, so turn them into instants with [timeOf], not by adding them to today.
 */
data class TripPlan(val serviceDate: LocalDate, val itinerary: Itinerary) {
    val departureTime: Instant get() = timeOf(itinerary.departureSeconds)
    val arrivalTime: Instant get() = timeOf(itinerary.arrivalSeconds)

    /** The instant of [seconds] in this plan, such as a leg's start or end. */
    fun timeOf(seconds: Int): Instant = serviceTime(serviceDate, seconds)
}

/** When a trip should happen: setting off at the earliest, or arriving at the latest, then. */
sealed interface TripTime {
    val time: Instant

    data class DepartAt(override val time: Instant) : TripTime

    data class ArriveBy(override val time: Instant) : TripTime
}

/** Outcome of [TripPlanRepository.plan]; failures are values so the UI can explain them. */
sealed interface TripPlanResult {
    /**
     * At least one plan, fewest rides first; each later one arrives strictly earlier, or for
     * [TripTime.ArriveBy] leaves strictly later.
     */
    data class Found(val plans: List<TripPlan>) : TripPlanResult

    data class NoRoute(val reason: NoRouteReason) : TripPlanResult

    /**
     * No GTFS timetable on the device yet: the first download has not finished or failed, or an
     * app update dropped the old tables. Nearby stops trigger the download.
     */
    data object NoTimetable : TripPlanResult
}

/** Where trip plans come from; an interface so screens can be tested with a fake. */
fun interface TripPlanSource {
    /**
     * Ways from [origin] to [destination] setting off no earlier than a [TripTime.DepartAt], or
     * arriving no later than a [TripTime.ArriveBy] and leaving as late as possible.
     */
    suspend fun plan(origin: LatLng, destination: LatLng, time: TripTime): TripPlanResult
}

/**
 * Plans trips on the device from the imported timetable.
 *
 * Builds a [RoutePlanner] per service day the first time it is needed and keeps it until a
 * request no longer needs that day, so at most two networks (today and yesterday) stay in memory.
 * In the early morning it also searches the previous service day, whose last trips run past
 * midnight with times over 24:00:00, and keeps the best plans of both. "Arrive by" plans use the
 * same planners, whose mirrored networks are built on first use and kept with them.
 */
class TripPlanRepository(
    private val networks: TransitNetworkSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TripPlanSource {
    // Held while building, so overlapping requests for a new day build its network only once.
    private val lock = Mutex()
    private val planners = mutableMapOf<LocalDate, RoutePlanner>()

    /**
     * Ways from [origin] to [destination] at [time]; see [TripPlanSource.plan]. Searching runs on
     * [dispatcher]; the first request of a service day also reads its whole timetable.
     */
    override suspend fun plan(origin: LatLng, destination: LatLng, time: TripTime): TripPlanResult {
        val today = time.time.atZone(PRT_TIME_ZONE).toLocalDate()
        val days = listOf(today.minusDays(1), today).filter {
            secondsInto(it, time.time) <= LATEST_SERVICE_DAY_SECONDS
        }
        val dayPlanners = plannersFor(days) ?: return TripPlanResult.NoTimetable
        val results = withContext(dispatcher) {
            dayPlanners.map { (day, planner) ->
                val seconds = secondsInto(day, time.time)
                day to when (time) {
                    is TripTime.DepartAt -> planner.plan(origin, destination, seconds)
                    is TripTime.ArriveBy -> planner.planArrivingBy(origin, destination, seconds)
                }
            }
        }
        val plans = results.flatMap { (day, result) ->
            (result as? PlanResult.Found)?.itineraries.orEmpty().map { TripPlan(day, it) }
        }
        if (plans.isEmpty()) {
            // Both days share the same stops, so today's reason holds for yesterday too.
            return TripPlanResult.NoRoute((results.last().second as PlanResult.NoRoute).reason)
        }
        return TripPlanResult.Found(paretoFront(plans, arriveBy = time is TripTime.ArriveBy))
    }

    /** Planners for [days] in the same order, or null when there is no timetable. */
    private suspend fun plannersFor(days: List<LocalDate>): List<Pair<LocalDate, RoutePlanner>>? =
        lock.withLock {
            planners.keys.retainAll(days.toSet())
            days.map { day ->
                val planner = planners[day]
                    ?: RoutePlanner(networks.network(day) ?: return null).also {
                        planners[day] = it
                    }
                day to planner
            }
        }

    private fun secondsInto(serviceDate: LocalDate, instant: Instant): Int =
        Duration.between(serviceTime(serviceDate, 0), instant).seconds.toInt()

    private companion object {
        // How long after its start a service day's trips are still searched. PRT's latest
        // trips end around 26:45, so 30 hours (6 a.m. the next morning) leaves room.
        const val LATEST_SERVICE_DAY_SECONDS = 30 * 60 * 60
    }
}

/**
 * The same rule [RoutePlanner] applies within one day, across days: fewest rides first, and a
 * plan with more rides only when it arrives strictly earlier than every plan kept before it, or
 * when [arriveBy], leaves strictly later.
 */
private fun paretoFront(plans: List<TripPlan>, arriveBy: Boolean): List<TripPlan> {
    // Earlier is better for arrivals and later for departures, so departures are negated.
    val cost: (TripPlan) -> Long = if (arriveBy) {
        { -it.departureTime.epochSecond }
    } else {
        { it.arrivalTime.epochSecond }
    }
    val kept = mutableListOf<TripPlan>()
    for (plan in plans.sortedWith(compareBy({ it.itinerary.rides.size }, cost))) {
        if (kept.isEmpty() || cost(plan) < cost(kept.last())) kept += plan
    }
    return kept
}
