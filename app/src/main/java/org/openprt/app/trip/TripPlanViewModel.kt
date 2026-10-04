package org.openprt.app.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openprt.app.data.gtfs.RideStopsSource
import org.openprt.app.data.gtfs.TimetableDatesSource
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.gtfs.TripPlanResult
import org.openprt.app.data.gtfs.TripPlanSource
import org.openprt.app.data.gtfs.TripTime
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.orEmptyWhenNoData
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.WalkLeg

/** A leg as the plan list shows it. */
sealed interface LegSummary {
    /** Rounded up, so the user never gets less time than shown. */
    data class Walk(val minutes: Long) : LegSummary

    data class Ride(val route: String) : LegSummary
}

/**
 * One row of the plan list. When TrueTime predicts the first bus, [departureTime] and
 * [boardingTime] follow the prediction and [live] is true. Later legs have no live data, so
 * [arrivalTime] is the timetable's, pushed back only by as much of a late first bus as the
 * waits at transfers cannot absorb.
 */
data class TripOption(
    /** When to set off from the origin to reach the first bus. */
    val departureTime: Instant,
    val arrivalTime: Instant,
    /** From [departureTime] to [arrivalTime], rounded up. */
    val totalMinutes: Long,
    val transfers: Int,
    /** Walks of no length, e.g. when the origin is at the stop, are left out. */
    val legs: List<LegSummary>,
    val firstRoute: String,
    val boardingStopName: String,
    /** When the first bus leaves the boarding stop. */
    val boardingTime: Instant,
    val live: Boolean,
    /** The plan behind this row, for showing it on the map. */
    val plan: TripPlan,
    /** The "Arrive by" time the plan was made for; null for the other modes. */
    val deadline: Instant? = null
) {
    /** A late first bus is expected to get the rider there after [deadline]. */
    val late: Boolean get() = deadline != null && arrivalTime > deadline
}

/** How the user wants the trip timed. */
enum class TripTimeMode {
    /** Set off now; plans follow the clock. */
    LEAVE_NOW,

    /** Set off no earlier than a chosen time. */
    DEPART_AT,

    /** Get there no later than a chosen time, leaving as late as possible. */
    ARRIVE_BY
}

/**
 * The time controls above the plans. [at] is the chosen time, null only for
 * [TripTimeMode.LEAVE_NOW]. [dates] are the days the timetable covers, for the date picker; null
 * until they have been read or when there is no timetable.
 */
data class TripTimeUiState(
    val mode: TripTimeMode = TripTimeMode.LEAVE_NOW,
    val at: Instant? = null,
    val dates: ClosedRange<LocalDate>? = null
)

/** Looking up the live bus of a ride the user tapped in a chosen trip. */
sealed interface RideLookup {
    data object Idle : RideLookup

    data class Looking(val ride: RideLeg) : RideLookup

    /**
     * TrueTime predicts this bus at the ride's boarding stop; the screen opens its live details
     * and then calls [TripPlanActions.onRideOpened].
     */
    data class Live(val departure: DepartureItem) : RideLookup

    /** TrueTime has no prediction for this bus, or could not be asked; only its timetable is known. */
    data class ScheduledOnly(val ride: RideLeg) : RideLookup
}

/**
 * The option the user chose, shown leg by leg and on the map. [map] draws rides straight from
 * stop to stop until the stops they pass have been read from the timetable.
 */
data class SelectedTrip(
    val option: TripOption,
    val map: TripMapLayers,
    val ride: RideLookup = RideLookup.Idle
)

/** What the plan list shows while a destination is set. */
sealed interface TripPlanUiState {
    /** Searching, or waiting for the first location fix to search from. */
    data object Planning : TripPlanUiState

    /** At least one option, fewest rides first; [selected] is the one being looked at, if any. */
    data class Results(val options: List<TripOption>, val selected: SelectedTrip? = null) :
        TripPlanUiState

    data class NoRoute(val reason: NoRouteReason) : TripPlanUiState

    /** The timetable has not been downloaded yet; [TripPlanViewModel.retry] tries again. */
    data object NoTimetable : TripPlanUiState
}

/** What the trip panels can ask for; [TripPlanViewModel] does them. */
interface TripPlanActions {
    /** Searches again for the current destination, e.g. after the timetable has downloaded. */
    fun retry()

    /** Shows [option] leg by leg and on the map. */
    fun select(option: TripOption)

    /** Back from a chosen option to the list, without planning again. */
    fun closeSelection()

    /** Looks for the live bus of [ride] in the chosen option. */
    fun openRide(ride: RideLeg)

    /** The screen has opened the live bus found by [openRide]. */
    fun onRideOpened()

    /** Switches between Leave now, Depart at and Arrive by, and plans again. */
    fun setTimeMode(mode: TripTimeMode)

    /**
     * Sets the Depart at / Arrive by time and plans again; ignored for Leave now. A day the
     * timetable does not cover is replaced by the nearest one it does.
     */
    fun setTime(at: Instant)
}

/**
 * Plans a trip to the chosen destination, from a chosen starting point or else from the user's
 * location. New ends replace the plans, and clearing the destination cancels any search in
 * flight; `state` is null while there is no destination, so the screen shows the nearby
 * departures instead.
 *
 * Location updates do not plan again, so the list stays put while the user walks; only the first
 * known location (also after re-centering) starts a search that was waiting for one. With a
 * chosen starting point the user's location plays no part.
 *
 * The first bus of each option is looked up in TrueTime once, when the plans arrive; if that
 * fails the options keep their timetable times rather than failing the whole search.
 *
 * Plans that set off more than [LIVE_HORIZON] from now are not looked up: TrueTime does not
 * predict that far ahead.
 *
 * A chosen option is drawn from the origin the plans were made from, not where the user is now.
 * Its rides are traced through the stops [rideStops] reads from the timetable.
 *
 * [time] holds the Leave now / Depart at / Arrive by choice, which outlives the destination;
 * the days the timetable covers are read from [timetableDates] the first time a time is chosen.
 * Once they are known, a chosen time on a day outside them moves to the nearest covered day at
 * the same time of day, read in [zone], so no search runs on a day with no timetable.
 */
class TripPlanViewModel(
    private val source: TripPlanSource,
    private val predictions: PredictionSource,
    private val rideStops: RideStopsSource,
    private val clock: Clock,
    private val timetableDates: TimetableDatesSource = TimetableDatesSource { null },
    private val zone: ZoneId = ZoneId.systemDefault()
) : ViewModel(),
    TripPlanActions {
    private val mutableState = MutableStateFlow<TripPlanUiState?>(null)
    val state: StateFlow<TripPlanUiState?> = mutableState.asStateFlow()

    private val mutableTime = MutableStateFlow(TripTimeUiState())
    val time: StateFlow<TripTimeUiState> = mutableTime.asStateFlow()
    private var datesLoad: Job? = null

    private var userLocation: LatLng? = null
    private var chosenOrigin: LatLng? = null
    private var destination: LatLng? = null
    private var plannedFrom: LatLng? = null
    private var planning: Job? = null

    // Loads for the chosen option; cancelled when the choice or the plans change.
    private var selection: Job? = null
    private var rideLookup: Job? = null

    /** Reports the user's location; null while it is being looked up again. */
    fun onLocationChanged(location: LatLng?) {
        val waitingForLocation = userLocation == null && chosenOrigin == null
        userLocation = location
        if (waitingForLocation && location != null && destination != null) startPlanning()
    }

    /** Reports the destination, keeping the starting point; see [onEndpointsChanged]. */
    fun onDestinationChanged(location: LatLng?) = onEndpointsChanged(chosenOrigin, location)

    /**
     * Reports both ends at once, so a swap plans once. A null [origin] starts from the user's
     * location; a null [destination] clears the plans and returns to the nearby departures.
     */
    fun onEndpointsChanged(origin: LatLng?, destination: LatLng?) {
        if (origin == chosenOrigin && destination == this.destination) return
        chosenOrigin = origin
        this.destination = destination
        startPlanning()
    }

    override fun retry() {
        if (destination != null) startPlanning()
    }

    override fun setTimeMode(mode: TripTimeMode) {
        val current = mutableTime.value
        if (mode == current.mode) return
        val at = when (mode) {
            TripTimeMode.LEAVE_NOW -> null

            // The next whole minute, as the time picker shows whole minutes; rounding down
            // would list buses that left seconds ago.
            else -> current.at ?: withinTimetable(nextWholeMinute(clock.instant()))
        }
        mutableTime.value = current.copy(mode = mode, at = at)
        if (mode != TripTimeMode.LEAVE_NOW) loadTimetableDates()
        retry()
    }

    override fun setTime(at: Instant) {
        val current = mutableTime.value
        if (current.mode == TripTimeMode.LEAVE_NOW) return
        val covered = withinTimetable(at)
        if (covered == current.at) return
        mutableTime.value = current.copy(at = covered)
        retry()
    }

    private fun loadTimetableDates() {
        if (datesLoad != null) return
        datesLoad = viewModelScope.launch {
            val dates = timetableDates.dates()
            val current = mutableTime.value
            // The time was chosen before the dates were known, so it may lie outside them.
            val at = current.at?.let { withinTimetable(it, dates) }
            mutableTime.value = current.copy(at = at, dates = dates)
            if (at != current.at) retry()
            // No timetable yet: read again next time, it may have downloaded meanwhile.
            if (dates == null) datesLoad = null
        }
    }

    /** [at], moved to the nearest day in [dates] if it falls outside them; unchanged without dates. */
    private fun withinTimetable(
        at: Instant,
        dates: ClosedRange<LocalDate>? = mutableTime.value.dates
    ): Instant {
        if (dates == null) return at
        val local = at.atZone(zone).toLocalDateTime()
        val day = local.toLocalDate().coerceIn(dates.start, dates.endInclusive)
        return local.with(day).atZone(zone).toInstant()
    }

    override fun select(option: TripOption) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val from = plannedFrom ?: return
        val to = destination ?: return
        val itinerary = option.plan.itinerary
        cancelSelectionLoads()
        mutableState.value = results.copy(
            selected = SelectedTrip(option, itinerary.toMapLayers(from, to, emptyList()))
        )
        selection = viewModelScope.launch {
            val stops = itinerary.rides.map {
                rideStops.stopsBetween(it.tripId, it.from.stopId, it.to.stopId)
            }
            val layers = itinerary.toMapLayers(from, to, stops)
            updateSelected(option) { it.copy(map = layers) }
        }
    }

    override fun closeSelection() {
        cancelSelectionLoads()
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        mutableState.value = results.copy(selected = null)
    }

    override fun openRide(ride: RideLeg) {
        val option = (mutableState.value as? TripPlanUiState.Results)?.selected?.option ?: return
        updateSelected(option) { it.copy(ride = RideLookup.Looking(ride)) }
        rideLookup?.cancel()
        rideLookup = viewModelScope.launch {
            val found = findLiveBus(option, ride)
            updateSelected(option) {
                it.copy(ride = found?.let(RideLookup::Live) ?: RideLookup.ScheduledOnly(ride))
            }
        }
    }

    override fun onRideOpened() {
        val option = (mutableState.value as? TripPlanUiState.Results)?.selected?.option ?: return
        updateSelected(option) { it.copy(ride = RideLookup.Idle) }
    }

    private fun cancelSelectionLoads() {
        selection?.cancel()
        rideLookup?.cancel()
    }

    // A load that outlives its option must not change what replaced it.
    private fun updateSelected(option: TripOption, transform: (SelectedTrip) -> SelectedTrip) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val selected = results.selected?.takeIf { it.option == option } ?: return
        mutableState.value = results.copy(selected = transform(selected))
    }

    private suspend fun findLiveBus(option: TripOption, ride: RideLeg): DepartureItem? {
        val now = clock.instant()
        val stopId = ride.from.trueTimeStopId
        val result = predictions.predictions(listOf(stopId)).orEmptyWhenNoData()
        val live = (result as? TrueTimeResult.Success)?.value.orEmpty()
        val plan = option.plan
        val prediction = closestPrediction(
            live,
            ride,
            scheduled = plan.timeOf(ride.startSeconds),
            catchableFrom = now
        ) ?: return null
        return DepartureItem(
            route = prediction.route,
            direction = prediction.routeDirection,
            destination = prediction.destination,
            stopName = ride.from.name,
            walkMinutes = plan.itinerary.walkBefore(ride),
            minutesUntilDeparture = Duration.between(now, prediction.predictedTime).toMinutes(),
            delayed = prediction.delayed,
            stopId = prediction.stopId,
            vehicleId = prediction.vehicleId
        )
    }

    private fun startPlanning() {
        planning?.cancel()
        planning = null
        cancelSelectionLoads()
        val to = destination
        if (to == null) {
            mutableState.value = null
            return
        }
        mutableState.value = TripPlanUiState.Planning
        val from = chosenOrigin ?: userLocation ?: return
        plannedFrom = from
        // Cancelled before it can write if the destination changes, since both run on Main.
        planning = viewModelScope.launch { mutableState.value = plan(from, to) }
    }

    private suspend fun plan(from: LatLng, to: LatLng): TripPlanUiState {
        val now = clock.instant()
        val choice = mutableTime.value
        val time = when (choice.mode) {
            TripTimeMode.LEAVE_NOW -> TripTime.DepartAt(now)
            TripTimeMode.DEPART_AT -> TripTime.DepartAt(choice.at ?: now)
            TripTimeMode.ARRIVE_BY -> TripTime.ArriveBy(choice.at ?: now)
        }
        return when (val result = source.plan(from, to, time)) {
            is TripPlanResult.Found -> {
                val deadline = (time as? TripTime.ArriveBy)?.time
                val options = options(result.plans, now, deadline)
                // Searching back from the deadline also finds ways that set off before now.
                val catchable = if (deadline == null) {
                    options
                } else {
                    options.filter { it.departureTime >= now }
                }
                if (catchable.isEmpty()) {
                    TripPlanUiState.NoRoute(NoRouteReason.NO_CONNECTION)
                } else {
                    TripPlanUiState.Results(catchable)
                }
            }

            is TripPlanResult.NoRoute -> TripPlanUiState.NoRoute(result.reason)

            TripPlanResult.NoTimetable -> TripPlanUiState.NoTimetable
        }
    }

    private suspend fun options(
        plans: List<TripPlan>,
        now: Instant,
        deadline: Instant?
    ): List<TripOption> {
        val soon = plans.filter { it.departureTime <= now + LIVE_HORIZON }
        val stopIds = soon.map { it.itinerary.rides.first().from.trueTimeStopId }.distinct()
        val live = if (stopIds.isEmpty()) {
            emptyList()
        } else {
            when (val result = predictions.predictions(stopIds).orEmptyWhenNoData()) {
                is TrueTimeResult.Success -> result.value

                // The timetable alone still gives usable plans.
                is TrueTimeResult.Failure -> emptyList()
            }
        }
        return plans.map { it.toOption(if (it in soon) live else emptyList(), now, deadline) }
    }
}

/** [now] rounded up to a whole minute, so a bus leaving at the chosen minute can still be caught. */
private fun nextWholeMinute(now: Instant): Instant {
    val minute = now.truncatedTo(ChronoUnit.MINUTES)
    return if (minute < now) minute.plus(1, ChronoUnit.MINUTES) else minute
}

/** How far ahead TrueTime predicts buses, about; later plans keep their timetable times. */
private val LIVE_HORIZON: Duration = Duration.ofHours(1)

/** How far a prediction may be from the scheduled boarding time to count as the same bus. */
private val MAX_LIVE_OFFSET: Duration = Duration.ofMinutes(15)

/**
 * This plan as a list row. The first bus takes the time of the [predictions] entry for its route
 * at its boarding stop that the user can still walk to by [now] and that is closest to the
 * timetable, within [MAX_LIVE_OFFSET]; without one the timetable time stays. [deadline] is the
 * "Arrive by" time, if any, so the row can warn when a late bus would miss it.
 */
internal fun TripPlan.toOption(
    predictions: List<Prediction>,
    now: Instant,
    deadline: Instant? = null
): TripOption {
    val firstRide = itinerary.rides.first()
    val scheduledBoarding = timeOf(firstRide.startSeconds)
    val walkToBus = Duration.ofSeconds(
        (firstRide.startSeconds - itinerary.departureSeconds).toLong()
    )
    val live = closestPrediction(predictions, firstRide, scheduledBoarding, now + walkToBus)
    val boardingTime = live?.predictedTime ?: scheduledBoarding
    val departureTime = boardingTime - walkToBus
    // An early first bus does not make the later legs leave earlier, so only lateness counts.
    val lateSeconds = Duration.between(scheduledBoarding, boardingTime).seconds.coerceAtLeast(0)
    val expectedArrival = arrivalTime.plusSeconds(itinerary.delayAtEnd(lateSeconds))
    return TripOption(
        departureTime = departureTime,
        arrivalTime = expectedArrival,
        totalMinutes = Duration.between(departureTime, expectedArrival).roundedUpMinutes(),
        transfers = itinerary.transfers,
        legs = itinerary.legs.mapNotNull { leg ->
            when (leg) {
                is RideLeg -> LegSummary.Ride(leg.routeId)

                is WalkLeg -> {
                    val minutes = leg.minutes()
                    if (minutes > 0) LegSummary.Walk(minutes) else null
                }
            }
        },
        firstRoute = firstRide.routeId,
        boardingStopName = firstRide.from.name,
        boardingTime = boardingTime,
        live = live != null,
        plan = this,
        deadline = deadline
    )
}

/**
 * The entry of [predictions] for [ride]'s route at its boarding stop that leaves no earlier than
 * [catchableFrom] and closest to the [scheduled] time, within [MAX_LIVE_OFFSET]; null if none.
 */
private fun closestPrediction(
    predictions: List<Prediction>,
    ride: RideLeg,
    scheduled: Instant,
    catchableFrom: Instant
): Prediction? = predictions
    .filter {
        it.route == ride.routeId &&
            it.stopId == ride.from.trueTimeStopId &&
            it.predictedTime >= catchableFrom
    }
    .map { it to Duration.between(scheduled, it.predictedTime).abs() }
    .filter { (_, offset) -> offset <= MAX_LIVE_OFFSET }
    .minByOrNull { (_, offset) -> offset }
    ?.first

/**
 * How late the trip ends when its first bus leaves [lateSeconds] late: each later ride starts
 * on time if the delay fits in the wait before it, and otherwise the rest of the delay carries
 * on, as if a later bus of that route ran the same timetable shifted back.
 */
private fun Itinerary.delayAtEnd(lateSeconds: Long): Long {
    var delay = lateSeconds
    legs.zipWithNext().forEach { (previous, next) ->
        if (next is RideLeg && next != rides.first()) {
            val wait = (next.startSeconds - previous.endSeconds).coerceAtLeast(0)
            delay = (delay - wait).coerceAtLeast(0)
        }
    }
    return delay
}

/** Minutes of walking right before [ride], rounded up; 0 after a ride or a wait. */
private fun Itinerary.walkBefore(ride: RideLeg): Long {
    val walk = legs.getOrNull(legs.indexOf(ride) - 1) as? WalkLeg ?: return 0
    return walk.minutes()
}

/** How long this walk takes, rounded up so the user never gets less time than shown. */
internal fun WalkLeg.minutes(): Long =
    Duration.ofSeconds((endSeconds - startSeconds).toLong()).roundedUpMinutes()

private fun Duration.roundedUpMinutes(): Long = (seconds + 59) / 60
