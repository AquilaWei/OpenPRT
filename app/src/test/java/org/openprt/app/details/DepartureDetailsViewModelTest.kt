package org.openprt.app.details

import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.truetime.Pattern
import org.openprt.app.data.truetime.PatternPoint
import org.openprt.app.data.truetime.PatternStop
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
        val viewModel = DepartureDetailsViewModel(FakeTripSource())

        assertNull(viewModel.state.value)
    }

    @Test
    fun open_beforeRouteArrives_showsDepartureWithRouteLoading() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource())

        viewModel.open(DEPARTURE)

        assertEquals(
            DepartureDetailsUiState(DEPARTURE, RouteStatus.Loading),
            viewModel.state.value
        )
    }

    @Test
    fun open_asksForTheBusOfTheDeparture() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(listOf(listOf("5601")), source.vehicleRequests)
    }

    @Test
    fun open_requestsThePatternTheBusIsOn() = runTest(dispatcher) {
        val source = FakeTripSource()
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(listOf(4512), source.patternRequests)
    }

    @Test
    fun open_patternLoaded_routeIsReadyWithBoardingStop() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource())

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(
            DepartureDetailsUiState(
                DEPARTURE,
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
                            )
                    )
                )
            ),
            viewModel.state.value
        )
    }

    @Test
    fun open_busNoLongerReported_routeIsNotFound() = runTest(dispatcher) {
        val source = FakeTripSource(
            vehicles = TrueTimeResult.Failure(
                TrueTimeError.Api(listOf("No data found for parameter"))
            )
        )
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(RouteStatus.NotFound, viewModel.state.value?.route)
    }

    @Test
    fun open_busNoLongerReported_doesNotRequestAPattern() = runTest(dispatcher) {
        val source = FakeTripSource(
            vehicles = TrueTimeResult.Failure(
                TrueTimeError.Api(listOf("No data found for parameter"))
            )
        )
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(emptyList<Int>(), source.patternRequests)
    }

    @Test
    fun open_vehicleRequestFails_routeIsFailed() = runTest(dispatcher) {
        val source = FakeTripSource(vehicles = TrueTimeResult.Failure(TrueTimeError.Timeout))
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(RouteStatus.Failed(TrueTimeError.Timeout), viewModel.state.value?.route)
    }

    @Test
    fun open_patternRequestFails_routeIsFailed() = runTest(dispatcher) {
        val error = TrueTimeError.Network(IOException("no network"))
        val source = FakeTripSource(patterns = TrueTimeResult.Failure(error))
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(RouteStatus.Failed(error), viewModel.state.value?.route)
    }

    @Test
    fun open_patternMissingFromResponse_routeIsNotFound() = runTest(dispatcher) {
        val source = FakeTripSource(patterns = TrueTimeResult.Success(emptyList()))
        val viewModel = DepartureDetailsViewModel(source)

        viewModel.open(DEPARTURE)
        runCurrent()

        assertEquals(RouteStatus.NotFound, viewModel.state.value?.route)
    }

    @Test
    fun close_afterRouteLoaded_returnsToNearbyList() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource())
        viewModel.open(DEPARTURE)
        runCurrent()

        viewModel.close()

        assertNull(viewModel.state.value)
    }

    @Test
    fun close_whileRouteLoading_staysClosedAfterwards() = runTest(dispatcher) {
        val viewModel = DepartureDetailsViewModel(FakeTripSource())
        viewModel.open(DEPARTURE)

        viewModel.close()
        runCurrent()

        assertNull(viewModel.state.value)
    }

    /** Answers with the given results and records what was asked for. */
    private class FakeTripSource(
        val vehicles: TrueTimeResult<List<Vehicle>> = TrueTimeResult.Success(listOf(BUS_5601)),
        val patterns: TrueTimeResult<List<Pattern>> = TrueTimeResult.Success(listOf(PATTERN_4512))
    ) : TripSource {
        val vehicleRequests = mutableListOf<List<String>>()
        val patternRequests = mutableListOf<Int>()

        override suspend fun vehicles(vehicleIds: List<String>): TrueTimeResult<List<Vehicle>> {
            vehicleRequests.add(vehicleIds)
            return vehicles
        }

        override suspend fun patterns(patternId: Int): TrueTimeResult<List<Pattern>> {
            patternRequests.add(patternId)
            return patterns
        }
    }

    private companion object {
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
