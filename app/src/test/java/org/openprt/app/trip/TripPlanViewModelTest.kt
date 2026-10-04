package org.openprt.app.trip

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.gtfs.RideStopsSource
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.gtfs.TripPlanResult
import org.openprt.app.data.gtfs.TripPlanSource
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.planner.Itinerary
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.TransitStop
import org.openprt.app.planner.WalkLeg

/**
 * Times: the plan is on Thursday 2026-10-01 (EDT, UTC-4); the clock reads 06:50 local. The
 * direct plan walks 200 s from 06:56:40 to board 61C at CMU at 07:00 (11:00Z).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripPlanViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_withoutDestination_isNull() {
        val viewModel = TripPlanViewModel(FakePlanSource(), NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)

        assertNull(viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_withLocation_plansFromLocationAtClockTime() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(HERE, THERE, NOW)), source.calls)
    }

    @Test
    fun onDestinationChanged_whilePlanning_isPlanning() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        runCurrent()

        assertEquals(TripPlanUiState.Planning, viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_plansFound_isResultsWithOneOptionPerPlan() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        runCurrent()

        source.answer.complete(TripPlanResult.Found(listOf(DIRECT_PLAN)))
        advanceUntilIdle()

        val state = viewModel.state.value as TripPlanUiState.Results
        assertEquals(listOf(DIRECT_PLAN), state.options.map { it.plan })
    }

    @Test
    fun onDestinationChanged_toNull_returnsToNearbyDepartures() = runTest(dispatcher) {
        val viewModel = TripPlanViewModel(FakePlanSource(), NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        viewModel.onDestinationChanged(null)

        assertNull(viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_toNullWhilePlanning_cancelsThePlanning() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        runCurrent()

        viewModel.onDestinationChanged(null)
        runCurrent()

        assertTrue(source.cancelled)
    }

    @Test
    fun onDestinationChanged_toNullWhilePlanning_lateResultIsNotShown() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        runCurrent()

        viewModel.onDestinationChanged(null)
        source.answer.complete(TripPlanResult.Found(listOf(DIRECT_PLAN)))
        advanceUntilIdle()

        assertNull(viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_beforeLocationIsKnown_isPlanningWithoutSearching() =
        runTest(dispatcher) {
            val source = FakePlanSource()
            val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)

            viewModel.onDestinationChanged(THERE)
            advanceUntilIdle()

            assertEquals(TripPlanUiState.Planning, viewModel.state.value)
            assertEquals(emptyList<PlanCall>(), source.calls)
        }

    @Test
    fun onLocationChanged_firstFixAfterDestination_plansFromIt() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onDestinationChanged(THERE)

        viewModel.onLocationChanged(HERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(HERE, THERE, NOW)), source.calls)
    }

    @Test
    fun onLocationChanged_afterPlanning_doesNotPlanAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        viewModel.onLocationChanged(LatLng(40.4450, -79.9540))
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    @Test
    fun onDestinationChanged_noRoute_isNoRouteWithReason() = runTest(dispatcher) {
        val source = FakePlanSource(
            CompletableDeferred(TripPlanResult.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION))
        )
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(
            TripPlanUiState.NoRoute(NoRouteReason.NO_STOP_NEAR_DESTINATION),
            viewModel.state.value
        )
    }

    @Test
    fun onDestinationChanged_noTimetable_isNoTimetable() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable))
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(TripPlanUiState.NoTimetable, viewModel.state.value)
    }

    @Test
    fun retry_withDestination_plansAgain() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable))
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, source.calls.size)
    }

    @Test
    fun onDestinationChanged_firstBusPredicted_asksTrueTimeAboutItsBoardingStop() =
        runTest(dispatcher) {
            val predictions = FakePredictionSource(TrueTimeResult.Success(emptyList()))
            val source =
                FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(DIRECT_PLAN))))
            val viewModel = TripPlanViewModel(source, predictions, STRAIGHT_RIDES, CLOCK)
            viewModel.onLocationChanged(HERE)

            viewModel.onDestinationChanged(THERE)
            advanceUntilIdle()

            assertEquals(listOf(listOf("8312")), predictions.requests)
        }

    @Test
    fun onDestinationChanged_firstBusPredicted_departsAtPredictedTimeAndIsLive() =
        runTest(dispatcher) {
            val predictions = FakePredictionSource(
                TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:03:00Z")))
            )
            val source =
                FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(DIRECT_PLAN))))
            val viewModel = TripPlanViewModel(source, predictions, STRAIGHT_RIDES, CLOCK)
            viewModel.onLocationChanged(HERE)

            viewModel.onDestinationChanged(THERE)
            advanceUntilIdle()

            val option = (viewModel.state.value as TripPlanUiState.Results).options.single()
            assertEquals(Instant.parse("2026-10-01T10:59:40Z"), option.departureTime)
            assertTrue(option.live)
        }

    @Test
    fun onDestinationChanged_predictionsFail_keepsTimetableDeparture() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Failure(TrueTimeError.Network(IOException("offline")))
        )
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(DIRECT_PLAN))))
        val viewModel = TripPlanViewModel(source, predictions, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        val option = (viewModel.state.value as TripPlanUiState.Results).options.single()
        assertEquals(Instant.parse("2026-10-01T10:56:40Z"), option.departureTime)
        assertFalse(option.live)
    }

    private data class PlanCall(val origin: LatLng, val destination: LatLng, val departAt: Instant)

    @Test
    fun select_option_isShownAsSelected() = runTest(dispatcher) {
        val viewModel = plannedViewModel(NO_PREDICTIONS)
        val option = viewModel.results().options.single()

        viewModel.select(option)

        assertEquals(option, viewModel.results().selected?.option)
    }

    @Test
    fun select_beforeRideStopsLoad_drawsRideStraightBetweenItsStops() = runTest(dispatcher) {
        val viewModel = plannedViewModel(NO_PREDICTIONS, rideStops = THROUGH_MIDDLE)

        viewModel.select(viewModel.results().options.single())

        assertEquals(listOf(listOf(HERE, THERE)), viewModel.results().selected?.map?.rides)
    }

    @Test
    fun select_afterRideStopsLoad_drawsRideThroughThem() = runTest(dispatcher) {
        val viewModel = plannedViewModel(NO_PREDICTIONS, rideStops = THROUGH_MIDDLE)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(listOf(listOf(HERE, MIDDLE, THERE)), viewModel.results().selected?.map?.rides)
    }

    @Test
    fun closeSelection_returnsToOptionsWithoutPlanningAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = plannedViewModel(NO_PREDICTIONS, source = source)
        viewModel.select(viewModel.results().options.single())

        viewModel.closeSelection()
        advanceUntilIdle()

        assertNull(viewModel.results().selected)
        assertEquals(1, source.calls.size)
    }

    @Test
    fun openRide_asksTrueTimeAboutItsBoardingStop() = runTest(dispatcher) {
        val predictions = FakePredictionSource(TrueTimeResult.Success(emptyList()))
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())

        viewModel.openRide(DIRECT_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        assertEquals(listOf("8312"), predictions.requests.last())
    }

    @Test
    fun openRide_busPredicted_isLiveWithThatBus() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:02:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())

        viewModel.openRide(DIRECT_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        val lookup = viewModel.results().selected?.ride as RideLookup.Live
        assertEquals(
            listOf("61C", "8312", "5501", "Forbes Ave at Morewood"),
            with(lookup.departure) { listOf(route, stopId, vehicleId, stopName) }
        )
    }

    @Test
    fun openRide_busPredicted_departureCountsMinutesAndWalkToTheStop() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:02:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())

        viewModel.openRide(DIRECT_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        val departure = (viewModel.results().selected?.ride as RideLookup.Live).departure
        // 10:50Z to 11:02Z is 12 minutes; the 200 s walk rounds up to 4.
        assertEquals(
            listOf(12L, 4L),
            listOf(departure.minutesUntilDeparture, departure.walkMinutes)
        )
    }

    @Test
    fun openRide_noPredictionForTheBus_isScheduledOnly() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("71B", "8312", "2026-10-01T11:02:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())
        val ride = DIRECT_PLAN.itinerary.rides.single()

        viewModel.openRide(ride)
        advanceUntilIdle()

        assertEquals(RideLookup.ScheduledOnly(ride), viewModel.results().selected?.ride)
    }

    @Test
    fun openRide_trueTimeFails_isScheduledOnly() = runTest(dispatcher) {
        val predictions = FakePredictionSource(TrueTimeResult.Failure(TrueTimeError.Timeout))
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())
        val ride = DIRECT_PLAN.itinerary.rides.single()

        viewModel.openRide(ride)
        advanceUntilIdle()

        assertEquals(RideLookup.ScheduledOnly(ride), viewModel.results().selected?.ride)
    }

    @Test
    fun onRideOpened_afterLiveBusFound_isIdleAgain() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:02:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())
        viewModel.openRide(DIRECT_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        viewModel.onRideOpened()

        assertEquals(RideLookup.Idle, viewModel.results().selected?.ride)
    }

    @Test
    fun onEndpointsChanged_chosenOrigin_plansFromItInsteadOfLocation() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(MIDDLE, THERE, NOW)), source.calls)
    }

    @Test
    fun onEndpointsChanged_chosenOriginWithoutLocation_plansRightAway() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)

        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(MIDDLE, THERE, NOW)), source.calls)
    }

    @Test
    fun onLocationChanged_chosenOrigin_doesNotPlanAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        viewModel.onLocationChanged(HERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(MIDDLE, THERE, NOW)), source.calls)
    }

    @Test
    fun onEndpointsChanged_originCleared_plansAgainFromLocation() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        viewModel.onEndpointsChanged(null, THERE)
        advanceUntilIdle()

        assertEquals(
            listOf(PlanCall(MIDDLE, THERE, NOW), PlanCall(HERE, THERE, NOW)),
            source.calls
        )
    }

    // What the screen reports after swapping with the start at "My location": the destination
    // is where the user was, the start is the old destination.
    @Test
    fun onEndpointsChanged_swappedFromLocation_plansOnceTheOtherWay() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onEndpointsChanged(null, THERE)
        advanceUntilIdle()

        viewModel.onEndpointsChanged(THERE, HERE)
        advanceUntilIdle()

        assertEquals(
            listOf(PlanCall(HERE, THERE, NOW), PlanCall(THERE, HERE, NOW)),
            source.calls
        )
    }

    @Test
    fun onEndpointsChanged_sameEnds_doesNotPlanAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    /** A view model that has planned [DIRECT_PLAN] from HERE to THERE. */
    private fun TestScope.plannedViewModel(
        predictions: PredictionSource,
        source: FakePlanSource = FakePlanSource(),
        rideStops: RideStopsSource = STRAIGHT_RIDES
    ): TripPlanViewModel {
        val viewModel = TripPlanViewModel(source, predictions, rideStops, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()
        return viewModel
    }

    private fun TripPlanViewModel.results() = state.value as TripPlanUiState.Results

    /** Answers every plan with [answer]; by default a direct plan right away. */
    private class FakePlanSource(
        val answer: CompletableDeferred<TripPlanResult> =
            CompletableDeferred(TripPlanResult.Found(listOf(DIRECT_PLAN)))
    ) : TripPlanSource {
        val calls = mutableListOf<PlanCall>()
        var cancelled = false

        override suspend fun plan(
            origin: LatLng,
            destination: LatLng,
            departAt: Instant
        ): TripPlanResult {
            calls += PlanCall(origin, destination, departAt)
            try {
                return answer.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private class FakePredictionSource(val result: TrueTimeResult<List<Prediction>>) :
        PredictionSource {
        val requests = mutableListOf<List<String>>()

        override suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>> {
            requests += stopIds
            return result
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T10:50:00Z")
        val CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
        val HERE = LatLng(40.4443, -79.9532)
        val THERE = LatLng(40.4406, -79.9959)
        val NO_PREDICTIONS = PredictionSource { TrueTimeResult.Success(emptyList()) }
        val MIDDLE = LatLng(40.4420, -79.9750)

        /** No stops read, so rides stay straight lines. */
        val STRAIGHT_RIDES = RideStopsSource { _, _, _ -> emptyList() }
        val THROUGH_MIDDLE = RideStopsSource { _, _, _ -> listOf(HERE, MIDDLE, THERE) }

        val CMU = TransitStop("s8312", "Forbes Ave at Morewood", HERE, trueTimeStopId = "8312")
        val STEEL_PLAZA = TransitStop("s10", "Steel Plaza", THERE, trueTimeStopId = "10")

        val DIRECT_PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 240.0, 25_000, 25_200),
                    RideLeg("T1", "61C", "DOWNTOWN", CMU, STEEL_PLAZA, 25_200, 27_000),
                    WalkLeg(STEEL_PLAZA, null, 120.0, 27_000, 27_100)
                )
            )
        )

        fun prediction(route: String, stopId: String, time: String) = Prediction(
            generatedAt = NOW,
            type = PredictionType.DEPARTURE,
            stopId = stopId,
            stopName = "Forbes Ave at Morewood",
            vehicleId = "5501",
            distanceToStopFeet = 1000,
            route = route,
            routeDirection = "INBOUND",
            destination = "Downtown",
            predictedTime = Instant.parse(time),
            delayed = false
        )
    }
}
