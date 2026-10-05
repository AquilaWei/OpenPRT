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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import org.openprt.app.walk.WalkPath
import org.openprt.app.walk.WalkRouter

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
 * waits at transfers cannot absorb. Once the walks of the chosen option are routed along the
 * streets ([withWalks]), its walking minutes, [departureTime] and [arrivalTime] follow them.
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
    val deadline: Instant? = null,
    /**
     * A walk takes longer along the streets than the planner allowed, more than the time there
     * is to spare: the rider would have had to set off before now for the first bus, or reaches a
     * transfer stop after the next bus has left.
     */
    val missesBus: Boolean = false,
    /** At least one walk takes longer along the streets than the planner's estimate. */
    val walksLonger: Boolean = false
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
 * stop to stop until the stops they pass have been read from the timetable, and walks straight
 * until a street route is found for them.
 */
data class SelectedTrip(
    val option: TripOption,
    val map: TripMapLayers,
    val ride: RideLookup = RideLookup.Idle,
    /** One per walk of the itinerary, in order; empty or [WalkPath.Straight] until routed. */
    val walks: List<WalkPath> = emptyList()
) {
    /** Minutes of [walk], one of this trip's walks: along the streets once routed, else straight. */
    fun minutesOf(walk: WalkLeg): Long {
        val index = option.plan.itinerary.legs.filterIsInstance<WalkLeg>().indexOf(walk)
        return (walks.getOrNull(index) as? WalkPath.Streets)?.minutes ?: walk.minutes()
    }
}

/**
 * This option timed with the walks of [walks], one per walk of the itinerary in order: a
 * [WalkPath.Streets] takes its own seconds, any other keeps the planner's estimate. The first bus
 * keeps its [TripOption.boardingTime], so the result is the same however often it is re-timed.
 * [now] tells whether the rider can still set off in time for the first bus.
 */
internal fun TripOption.withWalks(walks: List<WalkPath>, now: Instant): TripOption {
    val seconds = plan.itinerary.legs.filterIsInstance<WalkLeg>().mapIndexed { index, walk ->
        (walks.getOrNull(index) as? WalkPath.Streets)?.seconds ?: walk.seconds()
    }
    return plan.timed(boardingTime, live, deadline, seconds, now)
}

/** What the plan list shows while a destination is set. */
sealed interface TripPlanUiState {
    /** Searching, or waiting for the first location fix to search from. */
    data object Planning : TripPlanUiState

    /** At least one option, fewest rides first; [selected] is the one being looked at, if any. */
    data class Results(val options: List<TripOption>, val selected: SelectedTrip? = null) :
        TripPlanUiState

    data class NoRoute(val reason: NoRouteReason) : TripPlanUiState

    /**
     * The timetable has not been downloaded yet, and [importFailed] when the latest download
     * failed; [TripPlanViewModel.retry] searches again.
     */
    data class NoTimetable(val importFailed: Boolean) : TripPlanUiState
}

/** What the trip panels can ask for; [TripPlanViewModel] does them. */
interface TripPlanActions {
    /** Searches again for the current destination, e.g. after the timetable has downloaded. */
    fun retry()

    /** Shows [option] leg by leg and on the map. */
    fun select(option: TripOption)

    /** Back from a chosen option to the list, without planning again. */
    fun closeSelection()

    /**
     * Looks for the live bus of [ride] in the chosen option; a ride boarding more than an hour
     * from now is scheduled only, without asking TrueTime.
     */
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
 * Plans that set off more than [LIVE_HORIZON] from now are not looked up, and neither is a ride
 * the user taps that boards more than [LIVE_HORIZON] from now: TrueTime does not predict that far
 * ahead.
 *
 * A chosen option is drawn from the origin the plans were made from, not where the user is now.
 * Its rides are traced through the stops [rideStops] reads from the timetable, then its walks,
 * one at a time, along the streets [walkRouter] finds; walks it cannot route stay straight. Each
 * routed walk re-times the chosen option, both in the details and in its row of the list (see
 * [TripOption.withWalks]); the other options keep the planner's walking estimates.
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
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val walkRouter: WalkRouter = WalkRouter { from, to -> WalkPath.Straight(from, to) },
    private val timetableUpdates: Flow<Any?> = emptyFlow()
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

    init {
        viewModelScope.launch {
            timetableUpdates.collect {
                // Leave now with no dates read yet needs nothing: they are read fresh the first
                // time a time is chosen.
                if (datesLoad != null || mutableTime.value.mode != TripTimeMode.LEAVE_NOW) {
                    datesLoad?.cancel()
                    datesLoad = null
                    loadTimetableDates()
                }
                if (mutableState.value is TripPlanUiState.NoTimetable) retry()
            }
        }
    }

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
        val ends = itinerary.walkEnds(from, to)
        val walks: MutableList<WalkPath> =
            ends.map { (start, end) -> WalkPath.Straight(start, end) }.toMutableList()
        mutableState.value = results.copy(
            selected = SelectedTrip(
                option,
                itinerary.toMapLayers(from, to, emptyList(), emptyList()),
                walks = walks.toList()
            )
        )
        selection = viewModelScope.launch {
            val stops = itinerary.rides.map {
                rideStops.stopsBetween(it.tripId, it.from.stopId, it.to.stopId)
            }
            updateSelected(option) {
                it.copy(map = itinerary.toMapLayers(from, to, stops, emptyList()))
            }
            // One at a time, as the routing service allows a request a second; each walk shows
            // as soon as it is found.
            ends.forEachIndexed { index, (start, end) ->
                if (start == end) return@forEachIndexed
                walks[index] = walkRouter.route(start, end)
                val routed = walks.toList()
                val layers = itinerary.toMapLayers(from, to, stops, routed.map { it.points })
                updateSelected(option) { it.copy(map = layers, walks = routed) }
                retime(option, routed)
            }
        }
    }

    /** Puts the street walking times of [walks] into [option], in the details and the list. */
    private fun retime(option: TripOption, walks: List<WalkPath>) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val selected = results.selected?.takeIf { it.option.plan == option.plan } ?: return
        val timed = option.withWalks(walks, clock.instant())
        mutableState.value = results.copy(
            options = results.options.map { if (it.plan == option.plan) timed else it },
            selected = selected.copy(option = timed)
        )
    }

    override fun closeSelection() {
        cancelSelectionLoads()
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        mutableState.value = results.copy(selected = null)
    }

    override fun openRide(ride: RideLeg) {
        val option = (mutableState.value as? TripPlanUiState.Results)?.selected?.option ?: return
        rideLookup?.cancel()
        // TrueTime does not predict this far ahead, so asking it could only fail or mislead.
        if (option.plan.timeOf(ride.startSeconds) > clock.instant() + LIVE_HORIZON) {
            updateSelected(option) { it.copy(ride = RideLookup.ScheduledOnly(ride)) }
            return
        }
        updateSelected(option) { it.copy(ride = RideLookup.Looking(ride)) }
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

    // A load that outlives its option must not change what replaced it. Matched by plan, as
    // routed walks re-time the option itself.
    private fun updateSelected(option: TripOption, transform: (SelectedTrip) -> SelectedTrip) {
        val results = mutableState.value as? TripPlanUiState.Results ?: return
        val selected = results.selected?.takeIf { it.option.plan == option.plan } ?: return
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
            vehicleId = prediction.vehicleId,
            feed = prediction.feed
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

            is TripPlanResult.NoTimetable -> TripPlanUiState.NoTimetable(result.importFailed)
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
    val walkSeconds = itinerary.legs.filterIsInstance<WalkLeg>().map { it.seconds() }
    return timed(live?.predictedTime ?: scheduledBoarding, live != null, deadline, walkSeconds, now)
}

/**
 * This plan as a list row whose first bus leaves at [boardingTime] and whose walks take
 * [walkSeconds], one per walk in order. A walk longer than planned delays the rest of the trip
 * the same way a late first bus does (see [delayAtEnd]), and sets [TripOption.missesBus] when it
 * eats more than the time there is to spare.
 */
private fun TripPlan.timed(
    boardingTime: Instant,
    live: Boolean,
    deadline: Instant?,
    walkSeconds: List<Long>,
    now: Instant
): TripOption {
    val firstRide = itinerary.rides.first()
    val walks = itinerary.legs.filterIsInstance<WalkLeg>()
    // The first walk ends as the first bus leaves.
    val departureTime = boardingTime.minusSeconds(walkSeconds.first())
    // An early first bus does not make the later legs leave earlier, so only lateness counts.
    val lateSeconds = Duration.between(timeOf(firstRide.startSeconds), boardingTime)
        .seconds.coerceAtLeast(0)
    val expectedArrival = arrivalTime.plusSeconds(itinerary.delayAtEnd(lateSeconds, walkSeconds))
    val longerWalks = walks.indices.filter { walkSeconds[it] > walks[it].seconds() }
    // Only a longer walk counts: a plan that left before now was never offered as catchable.
    val missesFirstBus = 0 in longerWalks && departureTime < now
    val missesTransfer = itinerary.missesTransfer(walkSeconds)
    return TripOption(
        departureTime = departureTime,
        arrivalTime = expectedArrival,
        totalMinutes = Duration.between(departureTime, expectedArrival).roundedUpMinutes(),
        transfers = itinerary.transfers,
        legs = itinerary.legs.mapNotNull { leg ->
            when (leg) {
                is RideLeg -> LegSummary.Ride(leg.routeId)

                is WalkLeg -> {
                    val minutes = walkSeconds[walks.indexOf(leg)].roundedUpMinutes()
                    if (minutes > 0) LegSummary.Walk(minutes) else null
                }
            }
        },
        firstRoute = firstRide.routeId,
        boardingStopName = firstRide.from.name,
        boardingTime = boardingTime,
        live = live,
        plan = this,
        deadline = deadline,
        missesBus = missesFirstBus || missesTransfer,
        walksLonger = longerWalks.isNotEmpty()
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
 * How late the trip ends when its first bus leaves [lateSeconds] late and its walks take
 * [walkSeconds], one per walk in order: each walk after the first bus adds what it takes over
 * the planner's estimate, or takes off what it saves. Each later ride starts on time if the
 * delay fits in the wait before it, and otherwise the rest of the delay carries on, as if a
 * later bus of that route ran the same timetable shifted back. Negative when the last walk is
 * shorter than planned.
 */
private fun Itinerary.delayAtEnd(lateSeconds: Long, walkSeconds: List<Long>): Long {
    var delay = lateSeconds
    var walkIndex = 0
    legs.forEachIndexed { index, leg ->
        when (leg) {
            // The first walk only decides when to set off; the first bus keeps its own time.
            is WalkLeg -> {
                if (walkIndex > 0) delay += walkSeconds[walkIndex] - leg.seconds()
                walkIndex++
            }

            is RideLeg -> if (leg != rides.first()) {
                val wait = (leg.startSeconds - legs[index - 1].endSeconds).coerceAtLeast(0)
                delay = (delay - wait).coerceAtLeast(0)
            }
        }
    }
    return delay
}

/**
 * Whether a walk between two rides, taking [walkSeconds] (one per walk in order), runs over the
 * planner's estimate by more than the wait after it, so the rider reaches the stop after the bus
 * has left.
 */
private fun Itinerary.missesTransfer(walkSeconds: List<Long>): Boolean {
    val walks = legs.filterIsInstance<WalkLeg>()
    // The first walk is before any ride, so it cannot miss a transfer.
    return walks.indices.drop(1).any { index ->
        val walk = walks[index]
        val next = legs.getOrNull(legs.indexOf(walk) + 1) as? RideLeg ?: return@any false
        walkSeconds[index] - walk.seconds() > next.startSeconds - walk.endSeconds
    }
}

/** Minutes of walking right before [ride], rounded up; 0 after a ride or a wait. */
private fun Itinerary.walkBefore(ride: RideLeg): Long {
    val walk = legs.getOrNull(legs.indexOf(ride) - 1) as? WalkLeg ?: return 0
    return walk.minutes()
}

/** How long this walk takes, rounded up so the user never gets less time than shown. */
internal fun WalkLeg.minutes(): Long = seconds().roundedUpMinutes()

/** How long the planner allows for this walk. */
private fun WalkLeg.seconds(): Long = (endSeconds - startSeconds).toLong()

private fun Duration.roundedUpMinutes(): Long = seconds.roundedUpMinutes()

private fun Long.roundedUpMinutes(): Long = (this + 59) / 60
