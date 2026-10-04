package org.openprt.app.stop

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.gtfs.ScheduledRun
import org.openprt.app.data.gtfs.ScheduledStopTime
import org.openprt.app.data.gtfs.ScheduledTripSource
import org.openprt.app.data.gtfs.StopScheduleEntry
import org.openprt.app.data.gtfs.StopScheduleSource
import org.openprt.app.data.truetime.DataFeed
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.PredictionSource
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

@OptIn(ExperimentalCoroutinesApi::class)
class StopDeparturesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedClock(NOW)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_beforeAnyStopIsSelected_isNull() {
        val viewModel =
            StopDeparturesViewModel(FakePredictions(success()), FakeSchedule(), FakeTrips(), clock)

        assertNull(viewModel.state.value)
    }

    @Test
    fun select_beforeRefresh_showsStopLoading() {
        val viewModel =
            StopDeparturesViewModel(FakePredictions(success()), FakeSchedule(), FakeTrips(), clock)

        viewModel.select(FORBES, from = null)

        assertEquals(StopDeparturesUiState(FORBES), viewModel.state.value)
    }

    @Test
    fun autoRefresh_stopSelected_asksTrueTimeAboutThatStopRightAway() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117")), predictions.requests)
    }

    @Test
    fun autoRefresh_noStopSelected_makesNoRequest() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)

        val refreshing = launch { viewModel.autoRefresh() }
        advanceTimeBy(60_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(emptyList<List<String>>(), predictions.requests)
    }

    @Test
    fun autoRefresh_after30Seconds_asksAgain() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(2, predictions.requests.size)
    }

    @Test
    fun autoRefresh_lifecycleStopped_stopsAsking() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)
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

        assertEquals(1, predictions.requests.size)
    }

    @Test
    fun autoRefresh_closed_stopsAsking() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        viewModel.close()
        advanceTimeBy(90_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(1, predictions.requests.size)
    }

    @Test
    fun autoRefresh_livePredictions_listsThemSoonestFirstAsLive() = runTest(dispatcher) {
        val predictions = FakePredictions(
            success(prediction("71B", "Highland Park", 720), prediction("61C", "McKeesport", 300))
        )
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            StopDeparturesUiState(
                stop = FORBES,
                departures = listOf(
                    StopDeparture(
                        "61C",
                        "McKeesport",
                        5,
                        false,
                        departureItem("61C", "McKeesport", 5)
                    ),
                    StopDeparture(
                        "71B",
                        "Highland Park",
                        12,
                        false,
                        departureItem("71B", "Highland Park", 12)
                    )
                ),
                source = StopTimesSource.Live,
                lastUpdated = NOW
            ),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_lightRailPrediction_rowOpensDetailsOnTheLightRailFeed() = runTest(dispatcher) {
        val predictions = FakePredictions(
            success(prediction("RED", "Downtown", 300).copy(feed = DataFeed.LIGHT_RAIL))
        )
        val viewModel =
            StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            DataFeed.LIGHT_RAIL,
            viewModel.state.value?.departures?.single()?.departure?.feed
        )
    }

    // 139 m due south at 1.2 m/s is 116 s, rounded up to 2 minutes like the nearby list.
    @Test
    fun autoRefresh_selectedFromUserPosition_liveRowsCarryWalkToStop() = runTest(dispatcher) {
        val predictions = FakePredictions(success(prediction("61C", "McKeesport", 300)))
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = LatLng(40.44330, -79.94210))

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(2L, viewModel.state.value?.departures?.single()?.departure?.walkMinutes)
    }

    @Test
    fun autoRefresh_trueTimeFails_showsTimetableMarkedScheduled() = runTest(dispatcher) {
        val failure = TrueTimeError.Network(IOException("no network"))
        val schedule = FakeSchedule(SCHEDULED_ENTRY)
        val viewModel = StopDeparturesViewModel(
            FakePredictions(TrueTimeResult.Failure(failure)),
            schedule,
            FakeTrips(),
            clock
        )
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            StopDeparturesUiState(
                stop = FORBES,
                departures = listOf(SCHEDULED_ROW),
                source = StopTimesSource.Scheduled(failure),
                lastUpdated = NOW
            ),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_noApiKey_showsTimetableMarkedScheduled() = runTest(dispatcher) {
        val schedule = FakeSchedule(SCHEDULED_ENTRY)
        val viewModel = StopDeparturesViewModel(
            FakePredictions(TrueTimeResult.Failure(TrueTimeError.MissingApiKey)),
            schedule,
            FakeTrips(),
            clock
        )
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            StopDeparturesUiState(
                stop = FORBES,
                departures = listOf(SCHEDULED_ROW),
                source = StopTimesSource.Scheduled(TrueTimeError.MissingApiKey),
                lastUpdated = NOW
            ),
            viewModel.state.value
        )
    }

    @Test
    fun autoRefresh_noDataFound_showsTimetableWithoutError() = runTest(dispatcher) {
        val noData = TrueTimeError.Api(listOf("No data found for parameter"))
        val viewModel = StopDeparturesViewModel(
            FakePredictions(TrueTimeResult.Failure(noData)),
            FakeSchedule(SCHEDULED_ENTRY),
            FakeTrips(),
            clock
        )
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(StopTimesSource.Scheduled(null), viewModel.state.value?.source)
    }

    @Test
    fun autoRefresh_timetableFallback_asksScheduleForStopFromNow() = runTest(dispatcher) {
        val schedule = FakeSchedule()
        val viewModel = StopDeparturesViewModel(
            FakePredictions(TrueTimeResult.Failure(TrueTimeError.MissingApiKey)),
            schedule,
            FakeTrips(),
            clock
        )
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf("7117" to NOW), schedule.requests)
    }

    @Test
    fun autoRefresh_predictionsForOtherStops_areIgnored() = runTest(dispatcher) {
        val elsewhere = prediction("61C", "McKeesport", 300).copy(stopId = "2635")
        val viewModel = StopDeparturesViewModel(
            FakePredictions(success(elsewhere)),
            FakeSchedule(),
            FakeTrips(),
            clock
        )
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(emptyList<StopDeparture>(), viewModel.state.value?.departures)
    }

    @Test
    fun close_afterSelect_clearsState() {
        val viewModel =
            StopDeparturesViewModel(FakePredictions(success()), FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        viewModel.close()

        assertNull(viewModel.state.value)
    }

    @Test
    fun autoRefresh_otherStopSelected_asksAboutNewStopRightAway() = runTest(dispatcher) {
        val predictions = FakePredictions(success())
        val viewModel = StopDeparturesViewModel(predictions, FakeSchedule(), FakeTrips(), clock)
        viewModel.select(FORBES, from = null)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        viewModel.select(FIFTH, from = null)
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117"), listOf("2635")), predictions.requests)
    }

    @Test
    fun openScheduledTrip_timetabledRow_asksTimetableForItsRun() = runTest(dispatcher) {
        val trips = FakeTrips(RUN_STOPS)
        val viewModel = scheduledStopViewModel(trips)

        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        assertEquals(listOf(RUN), trips.requests)
    }

    @Test
    fun openScheduledTrip_loaded_showsRunWithItsStops() = runTest(dispatcher) {
        val viewModel = scheduledStopViewModel(FakeTrips(RUN_STOPS))

        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        assertEquals(
            ScheduledTripUiState(SCHEDULED_ROW, RUN_STOPS),
            viewModel.state.value?.scheduledTrip
        )
    }

    @Test
    fun openScheduledTrip_beforeLoaded_showsRunLoading() = runTest(dispatcher) {
        val viewModel = scheduledStopViewModel(FakeTrips(RUN_STOPS, CompletableDeferred()))

        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        assertEquals(ScheduledTripUiState(SCHEDULED_ROW), viewModel.state.value?.scheduledTrip)
    }

    @Test
    fun openScheduledTrip_liveRow_opensNothing() = runTest(dispatcher) {
        val trips = FakeTrips(RUN_STOPS)
        val viewModel = scheduledStopViewModel(trips)

        viewModel.openScheduledTrip(
            StopDeparture("61C", "McKeesport", 5, false, departureItem("61C", "McKeesport", 5))
        )
        runCurrent()

        assertNull(viewModel.state.value?.scheduledTrip)
    }

    @Test
    fun back_runOpen_returnsToStopList() = runTest(dispatcher) {
        val viewModel = scheduledStopViewModel(FakeTrips(RUN_STOPS))
        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        viewModel.back()

        assertEquals(StopDeparturesUiState(FORBES), viewModel.state.value)
    }

    @Test
    fun back_noRunOpen_closesStop() {
        val viewModel = scheduledStopViewModel(FakeTrips())

        viewModel.back()

        assertNull(viewModel.state.value)
    }

    @Test
    fun openScheduledTrip_closedBeforeLoaded_staysClosed() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = scheduledStopViewModel(FakeTrips(RUN_STOPS, gate))
        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        viewModel.back()
        gate.complete(Unit)
        runCurrent()

        assertNull(viewModel.state.value?.scheduledTrip)
    }

    @Test
    fun autoRefresh_runOpen_keepsItOpen() = runTest(dispatcher) {
        val viewModel = scheduledStopViewModel(FakeTrips(RUN_STOPS))
        viewModel.openScheduledTrip(SCHEDULED_ROW)
        runCurrent()

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            ScheduledTripUiState(SCHEDULED_ROW, RUN_STOPS),
            viewModel.state.value?.scheduledTrip
        )
    }

    /** A view model with FORBES open, whose live times fail so the timetable is shown. */
    private fun scheduledStopViewModel(trips: FakeTrips) = StopDeparturesViewModel(
        FakePredictions(TrueTimeResult.Failure(TrueTimeError.MissingApiKey)),
        FakeSchedule(SCHEDULED_ENTRY),
        trips,
        clock
    ).apply { select(FORBES, from = null) }

    /**
     * The owner's state setter blocks on its dispatcher, so it gets an unconfined one: a
     * queued [StandardTestDispatcher] would never run while the test thread is blocked.
     */
    private fun lifecycleOwner() =
        TestLifecycleOwner(Lifecycle.State.STARTED, UnconfinedTestDispatcher(dispatcher.scheduler))

    /** Answers every request with [result] and records the requested stop IDs. */
    private class FakePredictions(val result: TrueTimeResult<List<Prediction>>) :
        PredictionSource {
        val requests = mutableListOf<List<String>>()

        override suspend fun predictions(stopIds: List<String>): TrueTimeResult<List<Prediction>> {
            requests.add(stopIds)
            return result
        }
    }

    /** Answers every request with [entries] and records the stop and time asked about. */
    private class FakeSchedule(vararg val entries: StopScheduleEntry) : StopScheduleSource {
        val requests = mutableListOf<Pair<String, Instant>>()

        override suspend fun departures(
            trueTimeStopId: String,
            after: Instant,
            limit: Int
        ): List<StopScheduleEntry> {
            requests.add(trueTimeStopId to after)
            return entries.toList()
        }
    }

    /** Answers every run with [stops]; [gate] holds the answer back until it is completed. */
    private class FakeTrips(
        private val stops: List<ScheduledStopTime> = emptyList(),
        private val gate: CompletableDeferred<Unit>? = null
    ) : ScheduledTripSource {
        val requests = mutableListOf<ScheduledRun>()

        override suspend fun stopsFrom(run: ScheduledRun): List<ScheduledStopTime> {
            requests.add(run)
            gate?.await()
            return stops
        }
    }

    private class FixedClock(val now: Instant) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T12:40:00Z")

        // 139 m north of the user position used in the walk test.
        val FORBES = StopMarker("7117", "Forbes Ave at Morewood", LatLng(40.44455, -79.94210))
        val FIFTH = StopMarker("2635", "Fifth Ave at Bellefield", LatLng(40.44577, -79.95142))

        val RUN = ScheduledRun("T2", LocalDate.of(2026, 10, 1), 2)

        val SCHEDULED_ENTRY =
            StopScheduleEntry("61C", "INBOUND-DOWNTOWN", NOW.plusSeconds(600), RUN)

        val SCHEDULED_ROW =
            StopDeparture("61C", "INBOUND-DOWNTOWN", 10, false, null, RUN, NOW.plusSeconds(600))

        val RUN_STOPS = listOf(
            ScheduledStopTime("FORBES AVE + MOREWOOD AVE", NOW.plusSeconds(600)),
            ScheduledStopTime("STEEL PLAZA STATION", NOW.plusSeconds(1800))
        )

        fun success(vararg predictions: Prediction) = TrueTimeResult.Success(predictions.toList())

        fun prediction(route: String, destination: String, secondsFromNow: Long) = Prediction(
            generatedAt = NOW,
            type = PredictionType.ARRIVAL,
            stopId = "7117",
            stopName = "Forbes Ave at Morewood",
            vehicleId = "5601",
            distanceToStopFeet = 4210,
            route = route,
            routeDirection = "OUTBOUND",
            destination = destination,
            predictedTime = NOW.plusSeconds(secondsFromNow),
            delayed = false
        )

        fun departureItem(route: String, destination: String, minutes: Long) = DepartureItem(
            route = route,
            direction = "OUTBOUND",
            destination = destination,
            stopName = "Forbes Ave at Morewood",
            walkMinutes = 0,
            minutesUntilDeparture = minutes,
            delayed = false,
            stopId = "7117",
            vehicleId = "5601"
        )
    }
}
