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
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.openprt.app.data.gtfs.TimetableDatesSource
import org.openprt.app.data.gtfs.TripPlan
import org.openprt.app.data.gtfs.TripPlanResult
import org.openprt.app.data.gtfs.TripPlanSource
import org.openprt.app.data.gtfs.TripTime
import org.openprt.app.data.truetime.DataFeed
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
import org.openprt.app.walk.WalkPath
import org.openprt.app.walk.WalkRouter

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

        assertEquals(listOf(PlanCall(HERE, THERE, LEAVE_NOW)), source.calls)
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

        assertEquals(listOf(PlanCall(HERE, THERE, LEAVE_NOW)), source.calls)
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
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable(false)))
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(TripPlanUiState.NoTimetable(importFailed = false), viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_noTimetableAfterAFailedDownload_saysTheDownloadFailed() =
        runTest(dispatcher) {
            val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable(true)))
            val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
            viewModel.onLocationChanged(HERE)

            viewModel.onDestinationChanged(THERE)
            advanceUntilIdle()

            assertEquals(TripPlanUiState.NoTimetable(importFailed = true), viewModel.state.value)
        }

    @Test
    fun retry_withDestination_plansAgain() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable(false)))
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

    private data class PlanCall(val origin: LatLng, val destination: LatLng, val time: TripTime)

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
    fun select_afterWalkRouted_drawsItAlongTheStreets() = runTest(dispatcher) {
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, CORNER, FORBES.location), 300))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(
            listOf(listOf(HERE, CORNER, FORBES.location)),
            viewModel.results().selected?.map?.walks
        )
    }

    @Test
    fun select_afterWalkRouted_showsItsStreetMinutes() = runTest(dispatcher) {
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, CORNER, FORBES.location), 300))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(5L, viewModel.results().selected?.minutesOf(WALK_TO_FORBES))
    }

    @Test
    fun select_walkLongerAlongStreets_setsOffEarlierInTheDetails() = runTest(dispatcher) {
        // 300 s instead of the planned 200 s before the 11:00Z bus.
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, CORNER, FORBES.location), 300))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(
            Instant.parse("2026-10-01T10:55:00Z"),
            viewModel.results().selected?.option?.departureTime
        )
    }

    @Test
    fun select_walkLongerAlongStreets_setsOffEarlierInTheList() = runTest(dispatcher) {
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, CORNER, FORBES.location), 300))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()
        viewModel.closeSelection()

        assertEquals(
            Instant.parse("2026-10-01T10:55:00Z"),
            viewModel.results().options.single().departureTime
        )
    }

    @Test
    fun select_walkTooLongToSetOffInTime_marksTheOptionMissingTheBus() = runTest(dispatcher) {
        // 700 s before 11:00Z means setting off at 10:48:20Z, before NOW.
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, CORNER, FORBES.location), 700))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertTrue(viewModel.results().selected?.option?.missesBus == true)
    }

    @Test
    fun select_arriveByLastWalkLongerPastTheDeadline_marksTheOptionLate() = runTest(dispatcher) {
        // Both walks take 400 s; the last one, planned at 100 s, ends at 11:36:40Z.
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, THERE), 400))
        val viewModel = plannedViewModel(NO_PREDICTIONS, twoWalkPlanSource(), walkRouter = router)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)
        viewModel.setTime(Instant.parse("2026-10-01T11:32:00Z"))
        advanceUntilIdle()

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertTrue(viewModel.results().selected?.option?.late == true)
    }

    @Test
    fun select_twoWalksRouted_drawsTheSecondAfterTheFirstReTimedTheOption() = runTest(dispatcher) {
        val router = FakeWalkRouter(WalkPath.Streets(listOf(HERE, THERE), 400))
        val viewModel = plannedViewModel(NO_PREDICTIONS, twoWalkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(
            listOf(listOf(HERE, THERE), listOf(HERE, THERE)),
            viewModel.results().selected?.map?.walks
        )
    }

    @Test
    fun select_walkNotRouted_staysStraightWithPlannedMinutes() = runTest(dispatcher) {
        val router = FakeWalkRouter(WalkPath.Straight(HERE, FORBES.location))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        val selected = viewModel.results().selected
        assertEquals(listOf(listOf(HERE, FORBES.location)), selected?.map?.walks)
        assertEquals(4L, selected?.minutesOf(WALK_TO_FORBES))
    }

    @Test
    fun select_walkOfNoLength_isNotRouted() = runTest(dispatcher) {
        // The plan's last walk ends at a stop on the destination itself.
        val router = FakeWalkRouter(WalkPath.Straight(HERE, FORBES.location))
        val viewModel = plannedViewModel(NO_PREDICTIONS, walkPlanSource(), walkRouter = router)

        viewModel.select(viewModel.results().options.single())
        advanceUntilIdle()

        assertEquals(listOf(HERE to FORBES.location), router.calls)
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
    fun openRide_lightRailPredicted_departureUsesTheLightRailFeed() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(
                listOf(
                    prediction("61C", "8312", "2026-10-01T11:02:00Z")
                        .copy(feed = DataFeed.LIGHT_RAIL)
                )
            )
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.select(viewModel.results().options.single())

        viewModel.openRide(DIRECT_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        val departure = (viewModel.results().selected?.ride as RideLookup.Live).departure
        assertEquals(DataFeed.LIGHT_RAIL, departure.feed)
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
    fun openRide_boardsMoreThanAnHourFromNow_doesNotAskTrueTime() = runTest(dispatcher) {
        val predictions = FakePredictionSource(TrueTimeResult.Success(emptyList()))
        val source =
            FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(TOMORROW_PLAN))))
        val viewModel = plannedViewModel(predictions, source = source)
        viewModel.select(viewModel.results().options.single())

        viewModel.openRide(TOMORROW_PLAN.itinerary.rides.single())
        advanceUntilIdle()

        assertEquals(emptyList<List<String>>(), predictions.requests)
    }

    @Test
    fun openRide_boardsMoreThanAnHourFromNow_isScheduledOnlyAtOnce() = runTest(dispatcher) {
        val source =
            FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(TOMORROW_PLAN))))
        val viewModel = plannedViewModel(NO_PREDICTIONS, source = source)
        viewModel.select(viewModel.results().options.single())
        val ride = TOMORROW_PLAN.itinerary.rides.single()

        viewModel.openRide(ride)

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

        assertEquals(listOf(PlanCall(MIDDLE, THERE, LEAVE_NOW)), source.calls)
    }

    @Test
    fun onEndpointsChanged_chosenOriginWithoutLocation_plansRightAway() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)

        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(MIDDLE, THERE, LEAVE_NOW)), source.calls)
    }

    @Test
    fun onLocationChanged_chosenOrigin_doesNotPlanAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, CLOCK)
        viewModel.onEndpointsChanged(MIDDLE, THERE)
        advanceUntilIdle()

        viewModel.onLocationChanged(HERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(MIDDLE, THERE, LEAVE_NOW)), source.calls)
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
            listOf(PlanCall(MIDDLE, THERE, LEAVE_NOW), PlanCall(HERE, THERE, LEAVE_NOW)),
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
            listOf(PlanCall(HERE, THERE, LEAVE_NOW), PlanCall(THERE, HERE, LEAVE_NOW)),
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

    @Test
    fun setTimeMode_arriveBy_plansArrivingByTheClockTime() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)

        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)
        advanceUntilIdle()

        assertEquals(PlanCall(HERE, THERE, TripTime.ArriveBy(NOW)), source.calls.last())
    }

    @Test
    fun setTime_departAt_plansSettingOffAtThatTime() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)
        viewModel.setTimeMode(TripTimeMode.DEPART_AT)

        viewModel.setTime(Instant.parse("2026-10-02T12:30:00Z"))
        advanceUntilIdle()

        assertEquals(
            PlanCall(HERE, THERE, TripTime.DepartAt(Instant.parse("2026-10-02T12:30:00Z"))),
            source.calls.last()
        )
    }

    @Test
    fun setTime_arriveBy_keepsTheModeAndShowsTheTime() = runTest(dispatcher) {
        val viewModel = plannedViewModel(NO_PREDICTIONS)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)

        viewModel.setTime(Instant.parse("2026-10-02T12:30:00Z"))

        assertEquals(TripTimeMode.ARRIVE_BY, viewModel.time.value.mode)
        assertEquals(Instant.parse("2026-10-02T12:30:00Z"), viewModel.time.value.at)
    }

    @Test
    fun setTime_leaveNow_isIgnored() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)

        viewModel.setTime(Instant.parse("2026-10-02T12:30:00Z"))
        advanceUntilIdle()

        assertEquals(1, source.calls.size)
    }

    @Test
    fun setTimeMode_optionSelected_clearsTheSelection() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(DIRECT_PLAN))))
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)
        viewModel.select(viewModel.results().options.single())

        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        assertNull(viewModel.results().selected)
    }

    @Test
    fun setTimeMode_backToLeaveNow_plansFromTheClockAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)
        advanceUntilIdle()

        viewModel.setTimeMode(TripTimeMode.LEAVE_NOW)
        advanceUntilIdle()

        assertEquals(PlanCall(HERE, THERE, LEAVE_NOW), source.calls.last())
    }

    @Test
    fun setTimeMode_departAt_readsTheTimetableDates() = runTest(dispatcher) {
        val dates = LocalDate.of(2026, 9, 27)..LocalDate.of(2026, 11, 21)
        val viewModel = TripPlanViewModel(
            FakePlanSource(),
            NO_PREDICTIONS,
            STRAIGHT_RIDES,
            CLOCK,
            TimetableDatesSource { dates }
        )

        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        assertEquals(dates, viewModel.time.value.dates)
    }

    @Test
    fun timetableUpdated_whileDepartAtIsOpen_readsTheNewTimetableDates() = runTest(dispatcher) {
        val newDates = LocalDate.of(2026, 10, 4)..LocalDate.of(2027, 1, 9)
        var dates = LocalDate.of(2026, 9, 27)..LocalDate.of(2026, 11, 21)
        val imports = MutableStateFlow(Instant.parse("2026-09-27T12:00:00Z"))
        val viewModel = TripPlanViewModel(
            FakePlanSource(),
            NO_PREDICTIONS,
            STRAIGHT_RIDES,
            CLOCK,
            TimetableDatesSource { dates },
            timetableUpdates = imports
        )
        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        dates = newDates
        imports.value = Instant.parse("2026-10-04T12:00:00Z")
        advanceUntilIdle()

        assertEquals(newDates, viewModel.time.value.dates)
    }

    @Test
    fun timetableUpdated_afterDatesWereReadInLeaveNow_readsTheNewDatesForDepartAt() =
        runTest(dispatcher) {
            val newDates = LocalDate.of(2026, 10, 4)..LocalDate.of(2027, 1, 9)
            var dates = LocalDate.of(2026, 9, 27)..LocalDate.of(2026, 11, 21)
            val imports = MutableStateFlow(Instant.parse("2026-09-27T12:00:00Z"))
            val viewModel = TripPlanViewModel(
                FakePlanSource(),
                NO_PREDICTIONS,
                STRAIGHT_RIDES,
                CLOCK,
                TimetableDatesSource { dates },
                timetableUpdates = imports
            )
            viewModel.setTimeMode(TripTimeMode.DEPART_AT)
            advanceUntilIdle()
            viewModel.setTimeMode(TripTimeMode.LEAVE_NOW)
            advanceUntilIdle()

            dates = newDates
            imports.value = Instant.parse("2026-10-04T12:00:00Z")
            advanceUntilIdle()
            viewModel.setTimeMode(TripTimeMode.DEPART_AT)
            advanceUntilIdle()

            assertEquals(newDates, viewModel.time.value.dates)
        }

    @Test
    fun timetableUpdated_whileShowingNoTimetable_plansAgain() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable(false)))
        val imports = MutableStateFlow<Instant?>(null)
        val viewModel = TripPlanViewModel(
            source,
            NO_PREDICTIONS,
            STRAIGHT_RIDES,
            CLOCK,
            timetableUpdates = imports
        )
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        imports.value = Instant.parse("2026-10-01T10:55:00Z")
        advanceUntilIdle()

        assertEquals(2, source.calls.size)
    }

    @Test
    fun setTimeMode_departAtTodayBeforeTheTimetable_plansOnItsFirstDay() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(
            source,
            NO_PREDICTIONS,
            STRAIGHT_RIDES,
            CLOCK,
            TimetableDatesSource { LocalDate.of(2026, 10, 2)..LocalDate.of(2026, 11, 21) },
            ZoneOffset.UTC
        )
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)

        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        assertEquals(
            PlanCall(HERE, THERE, TripTime.DepartAt(Instant.parse("2026-10-02T10:50:00Z"))),
            source.calls.last()
        )
    }

    @Test
    fun setTime_dayAfterTheTimetable_plansOnItsLastDayAtThatTime() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(
            source,
            NO_PREDICTIONS,
            STRAIGHT_RIDES,
            CLOCK,
            TimetableDatesSource { LocalDate.of(2026, 9, 27)..LocalDate.of(2026, 11, 21) },
            ZoneOffset.UTC
        )
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        viewModel.setTime(Instant.parse("2026-12-05T08:00:00Z"))
        advanceUntilIdle()

        assertEquals(
            PlanCall(HERE, THERE, TripTime.DepartAt(Instant.parse("2026-11-21T08:00:00Z"))),
            source.calls.last()
        )
    }

    @Test
    fun setTimeMode_departAtBetweenWholeMinutes_startsAtTheNextMinute() = runTest(dispatcher) {
        val source = FakePlanSource()
        val clock = Clock.fixed(Instant.parse("2026-10-01T10:50:30Z"), ZoneOffset.UTC)
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, STRAIGHT_RIDES, clock)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)

        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        advanceUntilIdle()

        assertEquals(
            PlanCall(HERE, THERE, TripTime.DepartAt(Instant.parse("2026-10-01T10:51:00Z"))),
            source.calls.last()
        )
    }

    @Test
    fun setTime_planLeavesMoreThanAnHourFromNow_doesNotAskTrueTime() = runTest(dispatcher) {
        val predictions = FakePredictionSource(TrueTimeResult.Success(emptyList()))
        val source =
            FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(TOMORROW_PLAN))))
        val viewModel = TripPlanViewModel(source, predictions, STRAIGHT_RIDES, CLOCK)
        viewModel.setTimeMode(TripTimeMode.DEPART_AT)
        viewModel.setTime(Instant.parse("2026-10-02T10:50:00Z"))
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(emptyList<List<String>>(), predictions.requests)
    }

    @Test
    fun setTimeMode_arriveByFirstBusLatePastTheDeadline_marksTheOptionLate() = runTest(dispatcher) {
        // The plan gets there at 11:31:40Z; five minutes late it misses 11:32Z.
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:05:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)

        viewModel.setTime(Instant.parse("2026-10-01T11:32:00Z"))
        advanceUntilIdle()

        assertTrue(viewModel.results().options.single().late)
    }

    @Test
    fun setTimeMode_arriveByFirstBusOnTime_isNotLate() = runTest(dispatcher) {
        val predictions = FakePredictionSource(
            TrueTimeResult.Success(listOf(prediction("61C", "8312", "2026-10-01T11:00:00Z")))
        )
        val viewModel = plannedViewModel(predictions)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)

        viewModel.setTime(Instant.parse("2026-10-01T11:32:00Z"))
        advanceUntilIdle()

        assertFalse(viewModel.results().options.single().late)
    }

    @Test
    fun setTime_arriveByPlanAlreadyLeft_dropsItAndKeepsTheCatchableOne() = runTest(dispatcher) {
        val source = FakePlanSource(
            CompletableDeferred(TripPlanResult.Found(listOf(MISSED_PLAN, DIRECT_PLAN)))
        )
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)

        viewModel.setTime(Instant.parse("2026-10-01T11:32:00Z"))
        advanceUntilIdle()

        assertEquals(
            listOf(Instant.parse("2026-10-01T10:56:40Z")),
            viewModel.results().options.map { it.departureTime }
        )
    }

    @Test
    fun setTime_arriveByEveryPlanAlreadyLeft_showsNoConnection() = runTest(dispatcher) {
        val source =
            FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(MISSED_PLAN))))
        val viewModel = plannedViewModel(NO_PREDICTIONS, source)
        viewModel.setTimeMode(TripTimeMode.ARRIVE_BY)

        viewModel.setTime(Instant.parse("2026-10-01T11:20:00Z"))
        advanceUntilIdle()

        assertEquals(TripPlanUiState.NoRoute(NoRouteReason.NO_CONNECTION), viewModel.state.value)
    }

    @Test
    fun leaveNow_planLeftBeforeNow_isStillListed() = runTest(dispatcher) {
        // Only "Arrive by" drops them; a departure search never returns plans that have left.
        val source =
            FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(MISSED_PLAN))))

        val viewModel = plannedViewModel(NO_PREDICTIONS, source)

        assertEquals(1, viewModel.results().options.size)
    }

    /** A view model that has planned [DIRECT_PLAN] from HERE to THERE. */
    private fun TestScope.plannedViewModel(
        predictions: PredictionSource,
        source: FakePlanSource = FakePlanSource(),
        rideStops: RideStopsSource = STRAIGHT_RIDES,
        walkRouter: WalkRouter = WalkRouter { from, to -> WalkPath.Straight(from, to) }
    ): TripPlanViewModel {
        val viewModel =
            TripPlanViewModel(source, predictions, rideStops, CLOCK, walkRouter = walkRouter)
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
            time: TripTime
        ): TripPlanResult {
            calls += PlanCall(origin, destination, time)
            try {
                return answer.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
    }

    private fun walkPlanSource() =
        FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(WALK_PLAN))))

    private fun twoWalkPlanSource() =
        FakePlanSource(CompletableDeferred(TripPlanResult.Found(listOf(TWO_WALK_PLAN))))

    /** Answers every walk with [path] and records which walks were asked for. */
    private class FakeWalkRouter(val path: WalkPath) : WalkRouter {
        val calls = mutableListOf<Pair<LatLng, LatLng>>()

        override suspend fun route(from: LatLng, to: LatLng): WalkPath {
            calls += from to to
            return path
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
        val LEAVE_NOW = TripTime.DepartAt(NOW)
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

        /** Sets off at 10:40Z, ten minutes before [NOW], and arrives at 11:15Z. */
        val MISSED_PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WalkLeg(null, CMU, 240.0, 24_000, 24_200),
                    RideLeg("T0", "61C", "DOWNTOWN", CMU, STEEL_PLAZA, 24_200, 26_000),
                    WalkLeg(STEEL_PLAZA, null, 120.0, 26_000, 26_100)
                )
            )
        )

        val CORNER = LatLng(40.4445, -79.9510)
        val FORBES = TransitStop("s7117", "Forbes Ave at Craig", LatLng(40.4447, -79.9483))
        val WALK_TO_FORBES = WalkLeg(null, FORBES, 240.0, 25_000, 25_200)

        /** Walks from HERE to a stop down the street, then rides to THERE. */
        val WALK_PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WALK_TO_FORBES,
                    RideLeg("T1", "61C", "DOWNTOWN", FORBES, STEEL_PLAZA, 25_200, 27_000),
                    WalkLeg(STEEL_PLAZA, null, 0.0, 27_000, 27_000)
                )
            )
        )

        val MIDDLE_STOP = TransitStop("s2000", "Fifth Ave at Atwood", MIDDLE)

        /**
         * Walks 200 s from HERE to a stop down the street, rides to a stop short of THERE at
         * 11:30Z, then walks 100 s to THERE, arriving 11:31:40Z.
         */
        val TWO_WALK_PLAN = TripPlan(
            LocalDate.of(2026, 10, 1),
            Itinerary(
                listOf(
                    WALK_TO_FORBES,
                    RideLeg("T1", "61C", "DOWNTOWN", FORBES, MIDDLE_STOP, 25_200, 27_000),
                    WalkLeg(MIDDLE_STOP, null, 120.0, 27_000, 27_100)
                )
            )
        )

        /** [DIRECT_PLAN] a day later, more than an hour from [NOW]. */
        val TOMORROW_PLAN = DIRECT_PLAN.copy(serviceDate = LocalDate.of(2026, 10, 2))

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
