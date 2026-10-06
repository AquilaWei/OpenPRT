package org.openprt.app.details

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.truetime.DataFeed
import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.PatternPoint
import org.openprt.app.data.truetime.PatternStop
import org.openprt.app.data.truetime.Prediction
import org.openprt.app.data.truetime.PredictionType
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.TrueTimeResult
import org.openprt.app.data.truetime.Vehicle
import org.openprt.app.departures.DepartureItem
import org.openprt.app.geo.LatLng
import org.openprt.app.map.StopMarker

@OptIn(ExperimentalCoroutinesApi::class)
class DepartureDetailsViewModelTest {
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
    fun state_beforeAnyDepartureIsOpened_isNull() {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)

        assertNull(viewModel.state.value)
    }

    @Test
    fun open_beforeRouteArrives_showsDepartureWithRouteLoading() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)

        viewModel.open(DEPARTURE)

        assertEquals(
            DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
            viewModel.state.value
        )
    }

    @Test
    fun open_asksForTheBusOfTheDeparture() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("5601")), source.vehicleRequests)
    }

    @Test
    fun open_lightRailDeparture_asksTheLightRailFeedForVehiclePatternAndPredictions() =
        runTest(dispatcher) {
            val source = FakeTripSource()
            val viewModel = DepartureDetailsViewModel(source, CLOCK)

            viewModel.open(DEPARTURE.copy(feed = DataFeed.LIGHT_RAIL))
            val refreshing = launch { viewModel.autoRefresh() }
            runCurrent()
            refreshing.cancel()

            assertEquals(
                listOf(DataFeed.LIGHT_RAIL, DataFeed.LIGHT_RAIL, DataFeed.LIGHT_RAIL),
                source.feeds
            )
        }

    @Test
    fun open_busDeparture_asksTheBusFeedForVehiclePatternAndPredictions() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(DataFeed.BUS, DataFeed.BUS, DataFeed.BUS), source.feeds)
    }

    @Test
    fun open_requestsThePatternTheBusIsOn() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(4512), source.patternRequests)
    }

    @Test
    fun open_patternLoaded_routeIsReadyWithBoardingStop() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            RouteStatus.Ready(
                RouteShape(
                    line = listOf(LatLng(40.440878, -79.999141), LatLng(40.444567, -79.942862)),
                    stops = listOf(
                        StopMarker(
                            "20690",
                            "Fifth Ave at Wood St",
                            LatLng(40.440878, -79.999141)
                        ),
                        StopMarker(
                            "7117",
                            "Forbes Ave at Morewood",
                            LatLng(40.444567, -79.942862)
                        )
                    ),
                    boardingStop =
                        StopMarker(
                            "7117",
                            "Forbes Ave at Morewood",
                            LatLng(40.444567, -79.942862)
                        ),
                    boardingDistanceFeet = 18620.0,
                    stopDistancesFeet = listOf(0.0, 18620.0)
                )
            ),
            viewModel.state.value?.route
        )
    }

    @Test
    fun open_busNoLongerReported_routeIsNotFound() = runTest(dispatcher) {
        val source = FakeTripSource(
            vehicles = TrueTimeResult.Failure(
                TrueTimeError.Api(listOf("No data found for parameter"))
            )
        )
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(RouteStatus.NotFound, viewModel.state.value?.route)
    }

    @Test
    fun open_busNoLongerReported_doesNotRequestAPattern() = runTest(dispatcher) {
        val source = FakeTripSource(
            vehicles = TrueTimeResult.Failure(
                TrueTimeError.Api(listOf("No data found for parameter"))
            )
        )
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(emptyList<Int>(), source.patternRequests)
    }

    @Test
    fun open_vehicleRequestFails_routeIsFailed() = runTest(dispatcher) {
        val source = FakeTripSource(vehicles = TrueTimeResult.Failure(TrueTimeError.Timeout))
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(RouteStatus.Failed(TrueTimeError.Timeout), viewModel.state.value?.route)
    }

    @Test
    fun open_patternRequestFails_routeIsFailed() = runTest(dispatcher) {
        val error = TrueTimeError.Network(IOException("no network"))
        val source = FakeTripSource(patterns = TrueTimeResult.Failure(error))
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(RouteStatus.Failed(error), viewModel.state.value?.route)
    }

    @Test
    fun open_patternMissingFromResponse_routeIsNotFound() = runTest(dispatcher) {
        val source = FakeTripSource(patterns = TrueTimeResult.Success(emptyList()))
        val viewModel = DepartureDetailsViewModel(source, CLOCK)

        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(RouteStatus.NotFound, viewModel.state.value?.route)
    }

    @Test
    fun close_afterRouteLoaded_returnsToNearbyList() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        viewModel.close()

        assertNull(viewModel.state.value)
    }

    @Test
    fun close_whileRequestsAreRunning_staysClosedAfterwards() = runTest(dispatcher) {
        val source = FakeTripSource(responseDelayMillis = 1_000)
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        viewModel.close()
        advanceTimeBy(5_000)
        runCurrent()
        refreshing.cancel()

        assertNull(viewModel.state.value)
    }

    @Test
    fun open_sameDepartureAgainAfterClose_refreshesAgainRightAway() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        val refreshing = launch { viewModel.autoRefresh() }
        viewModel.open(DEPARTURE)
        runCurrent()

        viewModel.close()
        viewModel.open(DEPARTURE)
        runCurrent()
        refreshing.cancel()

        assertEquals(2, source.vehicleRequests.size)
    }

    @Test
    fun autoRefresh_asksForPredictionsAtTheBoardingStop() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117")), source.predictionRequests)
    }

    @Test
    fun autoRefresh_after15Seconds_requestsVehicleAgain() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("5601"), listOf("5601")), source.vehicleRequests)
    }

    @Test
    fun autoRefresh_after15Seconds_requestsPredictionsAgain() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(listOf("7117"), listOf("7117")), source.predictionRequests)
    }

    @Test
    fun autoRefresh_before15Seconds_doesNotRequestAgain() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(14_999)
        runCurrent()
        refreshing.cancel()

        assertEquals(1, source.vehicleRequests.size)
    }

    @Test
    fun autoRefresh_routeAlreadyLoaded_doesNotRequestPatternAgain() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(listOf(4512), source.patternRequests)
    }

    @Test
    fun autoRefresh_routeFailedEarlier_retriesOnNextRefresh() = runTest(dispatcher) {
        val source = FakeTripSource(patterns = TrueTimeResult.Failure(TrueTimeError.Timeout))
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        source.patterns = TrueTimeResult.Success(listOf(PATTERN_4512))
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertTrue(viewModel.state.value?.route is RouteStatus.Ready)
    }

    @Test
    fun autoRefresh_busReported_showsItsPosition() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            BusPosition(LatLng(40.43851, -79.92284), 145),
            viewModel.state.value?.bus?.position
        )
    }

    @Test
    fun autoRefresh_busBetweenStops_reportsStopsPassedAndStopsAway() = runTest(dispatcher) {
        // BUS_5601 is 12345 ft along, between the stops at 0 ft and 18620 ft (boarding).
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(
            BusProgress(passedStops = 1, stopsAway = 1),
            viewModel.state.value?.bus?.progress
        )
    }

    @Test
    fun autoRefresh_busMoved_markerMovesWithIt() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        source.vehicles = TrueTimeResult.Success(
            listOf(BUS_5601.copy(latitude = 40.4402, longitude = -79.9560, headingDegrees = 90))
        )
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(
            BusPosition(LatLng(40.4402, -79.9560), 90),
            viewModel.state.value?.bus?.position
        )
    }

    @Test
    fun autoRefresh_busNoLongerReported_hasNoPosition() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        source.vehicles = TrueTimeResult.Failure(
            TrueTimeError.Api(listOf("No data found for parameter"))
        )
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertNull(viewModel.state.value?.bus?.position)
    }

    @Test
    fun autoRefresh_busPredicted_arrivalMinutesComeFromPredictionAndClock() = runTest(dispatcher) {
        // CLOCK is 12:40:00 and the bus is predicted at 12:47:30.
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(Arrival.Expected(7, delayed = false), viewModel.state.value?.bus?.arrival)
    }

    @Test
    fun autoRefresh_busPredictedDelayed_arrivalIsMarkedDelayed() = runTest(dispatcher) {
        val source = FakeTripSource(
            predictions = TrueTimeResult.Success(listOf(PREDICTION_5601.copy(delayed = true)))
        )
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(Arrival.Expected(7, delayed = true), viewModel.state.value?.bus?.arrival)
    }

    @Test
    fun autoRefresh_busPastBoardingStop_hasDeparted() = runTest(dispatcher) {
        // The boarding stop is 18620 ft along the pattern; the prediction is still there.
        val source = FakeTripSource(
            vehicles = TrueTimeResult.Success(
                listOf(BUS_5601.copy(distanceAlongPatternFeet = 19000))
            )
        )
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(Arrival.Departed, viewModel.state.value?.bus?.arrival)
    }

    @Test
    fun autoRefresh_predictionDisappeared_hasDeparted() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        source.predictions = TrueTimeResult.Failure(
            TrueTimeError.Api(listOf("No data found for parameter"))
        )
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(Arrival.Departed, viewModel.state.value?.bus?.arrival)
    }

    @Test
    fun autoRefresh_onlyOtherBusesPredicted_hasDeparted() = runTest(dispatcher) {
        val source = FakeTripSource(
            predictions = TrueTimeResult.Success(listOf(PREDICTION_5601.copy(vehicleId = "5602")))
        )
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(Arrival.Departed, viewModel.state.value?.bus?.arrival)
    }

    @Test
    fun autoRefresh_succeeded_lastUpdatedIsNow() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource(), CLOCK)
        viewModel.open(DEPARTURE)

        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()
        refreshing.cancel()

        assertEquals(Instant.parse("2026-10-01T12:40:00Z"), viewModel.state.value?.bus?.lastUpdated)
    }

    @Test
    fun autoRefresh_predictionsFailLater_keepsLastArrivalAndReportsError() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        source.predictions = TrueTimeResult.Failure(TrueTimeError.Timeout)
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(
            LiveBus(
                position = BusPosition(LatLng(40.43851, -79.92284), 145),
                progress = BusProgress(passedStops = 1, stopsAway = 1),
                arrival = Arrival.Expected(7, delayed = false),
                lastUpdated = Instant.parse("2026-10-01T12:40:00Z"),
                error = TrueTimeError.Timeout
            ),
            viewModel.state.value?.bus
        )
    }

    @Test
    fun autoRefresh_offlineLater_keepsLastBusAndReportsOffline() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val refreshing = launch { viewModel.autoRefresh() }
        runCurrent()

        val offline = TrueTimeError.Network(IOException("offline"))
        source.vehicles = TrueTimeResult.Failure(offline)
        source.predictions = TrueTimeResult.Failure(offline)
        advanceTimeBy(15_000)
        runCurrent()
        refreshing.cancel()

        assertEquals(
            LiveBus(
                position = BusPosition(LatLng(40.43851, -79.92284), 145),
                progress = BusProgress(passedStops = 1, stopsAway = 1),
                arrival = Arrival.Expected(7, delayed = false),
                lastUpdated = Instant.parse("2026-10-01T12:40:00Z"),
                error = offline
            ),
            viewModel.state.value?.bus
        )
    }

    @Test
    fun autoRefresh_lifecycleStopped_stopsRequesting() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source, CLOCK)
        viewModel.open(DEPARTURE)
        val owner = TestLifecycleOwner(
            Lifecycle.State.STARTED,
            UnconfinedTestDispatcher(testScheduler)
        )

        // The same binding MainActivity uses.
        val binding = launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.autoRefresh() }
        }
        runCurrent()
        owner.currentState = Lifecycle.State.CREATED
        advanceTimeBy(60_000)
        runCurrent()
        binding.cancel()

        assertEquals(1, source.vehicleRequests.size)
    }

    /** Answers with the given results, after [responseDelayMillis], and records the requests. */
    private class FakeTripSource(
        var vehicles: TrueTimeResult<List<Vehicle>> = TrueTimeResult.Success(listOf(BUS_5601)),
        var patterns: TrueTimeResult<List<Pattern>> = TrueTimeResult.Success(listOf(PATTERN_4512)),
        var predictions: TrueTimeResult<List<Prediction>> =
            TrueTimeResult.Success(listOf(PREDICTION_5601)),
        val responseDelayMillis: Long = 0
    ) : TripSource {
        val vehicleRequests = mutableListOf<List<String>>()
        val patternRequests = mutableListOf<Int>()
        val predictionRequests = mutableListOf<List<String>>()

        /** The feed each call went to, in call order. */
        val feeds = mutableListOf<DataFeed>()

        override suspend fun vehicles(
            feed: DataFeed,
            vehicleIds: List<String>
        ): TrueTimeResult<List<Vehicle>> {
            feeds.add(feed)
            vehicleRequests.add(vehicleIds)
            delay(responseDelayMillis)
            return vehicles
        }

        override suspend fun patterns(
            feed: DataFeed,
            patternId: Int
        ): TrueTimeResult<List<Pattern>> {
            feeds.add(feed)
            patternRequests.add(patternId)
            delay(responseDelayMillis)
            return patterns
        }

        override suspend fun predictions(
            feed: DataFeed,
            stopIds: List<String>
        ): TrueTimeResult<List<Prediction>> {
            feeds.add(feed)
            predictionRequests.add(stopIds)
            delay(responseDelayMillis)
            return predictions
        }
    }

    private companion object {
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-01T12:40:00Z"), ZoneOffset.UTC)

        val DEPARTURE = DepartureItem(
            route = "61C",
            direction = "OUTBOUND",
            destination = "McKeesport",
            stopName = "Forbes Ave at Morewood",
            walkMinutes = 2,
            minutesUntilDeparture = 5,
            delayed = false,
            stopId = "7117",
            vehicleId = "5601"
        )

        val BUS_5601 = Vehicle(
            id = "5601",
            reportedAt = Instant.parse("2026-10-01T12:40:00Z"),
            latitude = 40.43851,
            longitude = -79.92284,
            headingDegrees = 145,
            patternId = 4512,
            route = "61C",
            destination = "McKeesport",
            distanceAlongPatternFeet = 12345,
            delayed = false
        )

        val PREDICTION_5601 = Prediction(
            generatedAt = Instant.parse("2026-10-01T12:40:00Z"),
            type = PredictionType.ARRIVAL,
            stopId = "7117",
            stopName = "Forbes Ave at Morewood",
            vehicleId = "5601",
            distanceToStopFeet = 6000,
            route = "61C",
            routeDirection = "OUTBOUND",
            destination = "McKeesport",
            predictedTime = Instant.parse("2026-10-01T12:47:30Z"),
            delayed = false
        )

        val PATTERN_4512 = Pattern(
            id = 4512,
            lengthFeet = 85432.0,
            routeDirection = "OUTBOUND",
            points = listOf(
                PatternPoint(
                    1,
                    40.440878,
                    -79.999141,
                    PatternStop("20690", "Fifth Ave at Wood St", 0.0)
                ),
                PatternPoint(
                    3,
                    40.444567,
                    -79.942862,
                    PatternStop("7117", "Forbes Ave at Morewood", 18620.0)
                )
            )
        )
    }
}
