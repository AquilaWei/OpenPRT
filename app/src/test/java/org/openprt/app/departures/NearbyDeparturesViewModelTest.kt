package org.openprt.app.departures

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.truetime.DataFeed
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyDeparturesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = MutableClock(NOW)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_beforeAnyRefresh_isLoadingAndEmpty() {
        val viewModel = NearbyDeparturesViewModel(FakePredictionSource(success()), clock)

        assertEquals(DeparturesUiState(), viewModel.state.value)
    }

    @Test
    fun autoRefresh_withStops_requestsPredictionsImmediately() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117")), source.requests)
    }

    @Test
    fun autoRefresh_after30Seconds_requestsPredictionsAgain() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(2, source.requests.size)
    }

    @Test
    fun autoRefresh_before30Seconds_doesNotRequestAgain() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(29_999)
        runCurrent()
        refreshing.cancel()

        assertEquals(1, source.requests.size)
    }

    @Test
    fun autoRefresh_beforeStopsAreKnown_makesNoRequest() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)

        val refreshing = launch { viewModel.autoRefresh() }
        advanceTimeBy(60_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(emptyList<List<String>>(), source.requests)
    }

    @Test
    fun autoRefresh_lifecycleStopped_stopsRequesting() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))
        val owner = lifecycleOwner()

        // The same binding MainActivity uses.
        val binding = launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.autoRefresh() }
        }
        runCurrent()
        owner.currentState = Lifecycle.State.CREATED
        advanceTimeBy(90_000)
        runCurrent()
        binding.cancel()

        assertEquals(1, source.requests.size)
    }

    @Test
    fun autoRefresh_lifecycleStartedAgain_refreshesRightAway() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))
        val owner = lifecycleOwner()

        val binding = launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.autoRefresh() }
        }
        runCurrent()
        owner.currentState = Lifecycle.State.CREATED
        advanceTimeBy(5_000)
        owner.currentState = Lifecycle.State.STARTED
        runCurrent()
        binding.cancel()

        assertEquals(2, source.requests.size)
    }

    @Test
    fun autoRefresh_stopsChanged_requestsNewStopsRightAway() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 120.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        viewModel.onStopsChanged(listOf(WalkableStop("2635", 80.0)))
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117"), listOf("2635")), source.requests)
    }

    @Test
    fun autoRefresh_moreThanTenStops_asksAboutTheTenNearest() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(
            listOf(
                WalkableStop("K", 390.0),
                WalkableStop("A", 10.0),
                WalkableStop("B", 20.0),
                WalkableStop("C", 30.0),
                WalkableStop("D", 40.0),
                WalkableStop("E", 50.0),
                WalkableStop("F", 60.0),
                WalkableStop("G", 70.0),
                WalkableStop("H", 80.0),
                WalkableStop("I", 90.0),
                WalkableStop("J", 100.0)
            )
        )

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            listOf(listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")),
            source.requests
        )
    }

    @Test
    fun autoRefresh_noStopsNearby_isReadyAndEmptyWithoutRequest() = runTest(dispatcher) {
        val source = FakePredictionSource(success())
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(emptyList())

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(emptyList<List<String>>(), source.requests)
        assertEquals(
            DeparturesUiState(emptyList(), DeparturesStatus.Ready, NOW),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_success_listsRankedDeparturesInMinutes() = runTest(dispatcher) {
        val source = FakePredictionSource(
            success(
                prediction("P1", "INBOUND", "Downtown", secondsFromNow = 725, delayed = true),
                prediction("61C", "OUTBOUND", "McKeesport", secondsFromNow = 300)
            )
        )
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 130.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            DeparturesUiState(
                departures = listOf(
                    DepartureItem(
                        "61C",
                        "OUTBOUND",
                        "McKeesport",
                        "Forbes Ave at Morewood Ave",
                        2,
                        5,
                        false,
                        "7117",
                        "5601"
                    ),
                    DepartureItem(
                        "P1",
                        "INBOUND",
                        "Downtown",
                        "Forbes Ave at Morewood Ave",
                        2,
                        12,
                        true,
                        "7117",
                        "5601"
                    )
                ),
                status = DeparturesStatus.Ready,
                lastUpdated = NOW
            ),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_busAndLightRailFeeds_asksEachFeedOnceAndListsLightRail() = runTest(dispatcher) {
        val bus = FakePredictionSource(
            success(prediction("61C", "OUTBOUND", "McKeesport", secondsFromNow = 300))
        )
        val lightRail = FakePredictionSource(
            success(
                prediction("RED", "INBOUND", "Downtown", secondsFromNow = 480).copy(
                    stopId = "99994",
                    stopName = "Steel Plaza Station",
                    vehicleId = "4301",
                    feed = DataFeed.LIGHT_RAIL
                )
            )
        )
        val viewModel =
            NearbyDeparturesViewModel(MergedPredictionSource(listOf(bus, lightRail)), clock)
        viewModel.onStopsChanged(
            listOf(WalkableStop("7117", 130.0), WalkableStop("99994", 130.0))
        )

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117", "99994")), bus.requests)
        assertEquals(listOf(listOf("7117", "99994")), lightRail.requests)
        assertEquals(
            listOf(
                DepartureItem(
                    "61C",
                    "OUTBOUND",
                    "McKeesport",
                    "Forbes Ave at Morewood Ave",
                    2,
                    5,
                    false,
                    "7117",
                    "5601"
                ),
                DepartureItem(
                    "RED",
                    "INBOUND",
                    "Downtown",
                    "Steel Plaza Station",
                    2,
                    8,
                    false,
                    "99994",
                    "4301",
                    DataFeed.LIGHT_RAIL
                )
            ),
            viewModel.state.value.departures
        )
    }

    @Test
    fun autoRefresh_noDataFoundError_isReadyAndEmpty() = runTest(dispatcher) {
        val source = FakePredictionSource(
            TrueTimeResult.Failure(TrueTimeError.Api(listOf("No data found for parameter")))
        )
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 130.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            DeparturesUiState(emptyList(), DeparturesStatus.Ready, NOW),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_otherApiError_isFailed() = runTest(dispatcher) {
        val error = TrueTimeError.Api(listOf("Invalid API access key supplied"))
        val viewModel = NearbyDeparturesViewModel(
            FakePredictionSource(TrueTimeResult.Failure(error)),
            clock
        )
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 130.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(DeparturesStatus.Failed(error), viewModel.state.value.status)
    }

    @Test
    fun autoRefresh_failureAfterSuccess_keepsListAndLastSuccessTime() = runTest(dispatcher) {
        val source = FakePredictionSource(
            success(prediction("61C", "OUTBOUND", "McKeesport", secondsFromNow = 600))
        )
        val viewModel = NearbyDeparturesViewModel(source, clock)
        viewModel.onStopsChanged(listOf(WalkableStop("7117", 130.0)))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        source.result = TrueTimeResult.Failure(TrueTimeError.Network(NETWORK_DOWN))
        clock.now = NOW.plusSeconds(30)
        advanceTimeBy(30_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(
            DeparturesUiState(
                departures = listOf(
                    DepartureItem(
                        "61C",
                        "OUTBOUND",
                        "McKeesport",
                        "Forbes Ave at Morewood Ave",
                        2,
                        10,
                        false,
                        "7117",
                        "5601"
                    )
                ),
                status = DeparturesStatus.Failed(TrueTimeError.Network(NETWORK_DOWN)),
                lastUpdated = NOW
            ),
            viewModel.state.value
        )
    }

    /**
     * The owner's state setter blocks on its dispatcher, so it gets an unconfined one: a
     * queued [StandardTestDispatcher] would never run while the test thread is blocked.
     */
    private fun lifecycleOwner() =
        TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(dispatcher.scheduler))

    /** Answers every request with [result] and records the requested stop IDs. */
    private class FakePredictionSource(var result: TrueTimeResult<List<Prediction>>) :
        PredictionSource {
        val requests = mutableListOf<List<String>>()

        override suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>> {
            requests.add(stopIds)
            return result
        }
    }

    /** A clock the test can move; the ViewModel only reads [instant]. */
    private class MutableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T12:40:00Z")
        val NETWORK_DOWN = IOException("no network")

        fun success(vararg predictions: Prediction) = TrueTimeResult.Success(predictions.toList())

        fun prediction(
            route: String,
            direction: String,
            destination: String,
            secondsFromNow: Long,
            delayed: Boolean = false
        ) = Prediction(
            generatedAt = NOW,
            type = PredictionType.ARRIVAL,
            stopId = "7117",
            stopName = "Forbes Ave at Morewood Ave",
            vehicleId = "5601",
            distanceToStopFeet = 4210,
            route = route,
            routeDirection = direction,
            destination = destination,
            predictedTime = NOW.plusSeconds(secondsFromNow),
            delayed = delayed
        )
    }
}
