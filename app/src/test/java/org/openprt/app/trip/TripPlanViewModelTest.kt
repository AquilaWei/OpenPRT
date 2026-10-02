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
        val viewModel = TripPlanViewModel(FakePlanSource(), NO_PREDICTIONS, CLOCK)

        assertNull(viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_withLocation_plansFromLocationAtClockTime() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(HERE, THERE, NOW)), source.calls)
    }

    @Test
    fun onDestinationChanged_whilePlanning_isPlanning() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        runCurrent()

        assertEquals(TripPlanUiState.Planning, viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_plansFound_isResultsWithOneOptionPerPlan() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
        val viewModel = TripPlanViewModel(FakePlanSource(), NO_PREDICTIONS, CLOCK)
        viewModel.onLocationChanged(HERE)
        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        viewModel.onDestinationChanged(null)

        assertNull(viewModel.state.value)
    }

    @Test
    fun onDestinationChanged_toNullWhilePlanning_cancelsThePlanning() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred())
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
            val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)

            viewModel.onDestinationChanged(THERE)
            advanceUntilIdle()

            assertEquals(TripPlanUiState.Planning, viewModel.state.value)
            assertEquals(emptyList<PlanCall>(), source.calls)
        }

    @Test
    fun onLocationChanged_firstFixAfterDestination_plansFromIt() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
        viewModel.onDestinationChanged(THERE)

        viewModel.onLocationChanged(HERE)
        advanceUntilIdle()

        assertEquals(listOf(PlanCall(HERE, THERE, NOW)), source.calls)
    }

    @Test
    fun onLocationChanged_afterPlanning_doesNotPlanAgain() = runTest(dispatcher) {
        val source = FakePlanSource()
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        assertEquals(TripPlanUiState.NoTimetable, viewModel.state.value)
    }

    @Test
    fun retry_withDestination_plansAgain() = runTest(dispatcher) {
        val source = FakePlanSource(CompletableDeferred(TripPlanResult.NoTimetable))
        val viewModel = TripPlanViewModel(source, NO_PREDICTIONS, CLOCK)
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
            val viewModel = TripPlanViewModel(source, predictions, CLOCK)
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
            val viewModel = TripPlanViewModel(source, predictions, CLOCK)
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
        val viewModel = TripPlanViewModel(source, predictions, CLOCK)
        viewModel.onLocationChanged(HERE)

        viewModel.onDestinationChanged(THERE)
        advanceUntilIdle()

        val option = (viewModel.state.value as TripPlanUiState.Results).options.single()
        assertEquals(Instant.parse("2026-10-01T10:56:40Z"), option.departureTime)
        assertFalse(option.live)
    }

    private data class PlanCall(val origin: LatLng, val destination: LatLng, val departAt: Instant)

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
