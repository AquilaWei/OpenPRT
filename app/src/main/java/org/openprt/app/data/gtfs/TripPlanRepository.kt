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

/** Outcome of [TripPlanRepository.plan]; failures are values so the UI can explain them. */
sealed interface TripPlanResult {
    /** At least one plan, fewest rides first; each later one arrives strictly earlier. */
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
    /** Ways from [origin] to [destination] setting off no earlier than [departAt]. */
    suspend fun plan(origin: LatLng, destination: LatLng, departAt: Instant): TripPlanResult
}

/**
 * Plans trips on the device from the imported timetable.
 *
 * Builds a [RoutePlanner] per service day the first time it is needed and keeps it until a
 * request no longer needs that day, so at most two networks (today and yesterday) stay in memory.
 * In the early morning it also searches the previous service day, whose last trips run past
 * midnight with times over 24:00:00, and keeps the best plans of both.
 */
class TripPlanRepository(
    private val networks: TransitNetworkSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : TripPlanSource {
    // Held while building, so overlapping requests for a new day build its network only once.
    private val lock = Mutex()
    private val planners = mutableMapOf<LocalDate, RoutePlanner>()

    /**
     * Ways from [origin] to [destination] setting off no earlier than [departAt]. Searching runs
     * on [dispatcher]; the first request of a service day also reads its whole timetable.
     */
    override suspend fun plan(
        origin: LatLng,
        destination: LatLng,
        departAt: Instant
    ): TripPlanResult {
        val today = departAt.atZone(PRT_TIME_ZONE).toLocalDate()
        val days = listOf(today.minusDays(1), today).filter {
            secondsInto(it, departAt) <= LATEST_SERVICE_DAY_SECONDS
        }
        val dayPlanners = plannersFor(days) ?: return TripPlanResult.NoTimetable
        val results = withContext(dispatcher) {
            dayPlanners.map { (day, planner) ->
                day to planner.plan(origin, destination, secondsInto(day, departAt))
            }
        }
        val plans = results.flatMap { (day, result) ->
            (result as? PlanResult.Found)?.itineraries.orEmpty().map { TripPlan(day, it) }
        }
        if (plans.isEmpty()) {
            // Both days share the same stops, so today's reason holds for yesterday too.
            return TripPlanResult.NoRoute((results.last().second as PlanResult.NoRoute).reason)
        }
        return TripPlanResult.Found(paretoFront(plans))
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
 * plan with more rides only when it arrives strictly earlier than every plan kept before it.
 */
private fun paretoFront(plans: List<TripPlan>): List<TripPlan> {
    val kept = mutableListOf<TripPlan>()
    val byRidesThenArrival = compareBy<TripPlan>({ it.itinerary.rides.size }, { it.arrivalTime })
    for (plan in plans.sortedWith(byRidesThenArrival)) {
        if (kept.isEmpty() || plan.arrivalTime < kept.last().arrivalTime) kept += plan
    }
    return kept
}
