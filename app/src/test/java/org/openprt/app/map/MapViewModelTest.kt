package org.openprt.app.map

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openprt.app.data.gtfs.GtfsImportError
import org.openprt.app.data.gtfs.NearbyStop
import org.openprt.app.data.gtfs.NearbyStopSource
import org.openprt.app.data.gtfs.NearbyStopsResult
import org.openprt.app.data.gtfs.StopEntity
import org.openprt.app.data.gtfs.TimetableDatesSource
import org.openprt.app.departures.WalkableStop
import org.openprt.app.geo.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
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
    fun state_beforeAnyLocation_isLoadingWithNoMarkers() {
        val viewModel = MapViewModel(FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS)))

        assertEquals(MapUiState(emptyList(), StopsStatus.Loading), viewModel.state.value)
    }

    @Test
    fun onLocationChanged_withNearbyStops_producesMarkerPerStop() = runTest(dispatcher) {
        val viewModel = MapViewModel(FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS)))

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        assertEquals(
            MapUiState(
                stopMarkers = listOf(
                    StopMarker(
                        "2635",
                        "FIFTH AVE + BELLEFIELD, OPP",
                        LatLng(40.445770, -79.951416)
                    ),
                    StopMarker("8312", "FORBES AVE + MOREWOOD AVE", LatLng(40.444557, -79.942791))
                ),
                stopsStatus = StopsStatus.Ready,
                walkableStops = listOf(WalkableStop("2635", 218.0), WalkableStop("8312", 880.0))
            ),
            viewModel.state.value
        )
    }

    @Test
    fun onLocationChanged_stopWithCode_walkableStopUsesStopCodeAsTrueTimeId() =
        runTest(dispatcher) {
            val steelPlaza = NearbyStop(
                StopEntity("10", "99994", "STEEL PLAZA STATION", 40.440277, -79.996529, 0),
                distanceMeters = 120.0
            )
            val viewModel = MapViewModel(
                FakeStopSource(NearbyStopsResult.Success(listOf(steelPlaza)))
            )

            viewModel.onLocationChanged(LatLng(40.4406, -79.9959))
            advanceUntilIdle()

            assertEquals(listOf(WalkableStop("99994", 120.0)), viewModel.state.value.walkableStops)
        }

    // A tapped marker asks TrueTime about its stop, so it carries the TrueTime ID too.
    @Test
    fun onLocationChanged_stopWithCode_markerUsesStopCodeAsTrueTimeId() = runTest(dispatcher) {
        val steelPlaza = NearbyStop(
            StopEntity("10", "99994", "STEEL PLAZA STATION", 40.440277, -79.996529, 0),
            distanceMeters = 120.0
        )
        val viewModel = MapViewModel(FakeStopSource(NearbyStopsResult.Success(listOf(steelPlaza))))

        viewModel.onLocationChanged(LatLng(40.4406, -79.9959))
        advanceUntilIdle()

        assertEquals(
            listOf(StopMarker("99994", "STEEL PLAZA STATION", LatLng(40.440277, -79.996529))),
            viewModel.state.value.stopMarkers
        )
    }

    @Test
    fun onLocationChanged_stopWithoutCode_walkableStopFallsBackToStopId() = runTest(dispatcher) {
        val noCode = NearbyStop(
            StopEntity("E12345", null, "SOME STOP", 40.440277, -79.996529, 0),
            distanceMeters = 120.0
        )
        val viewModel = MapViewModel(FakeStopSource(NearbyStopsResult.Success(listOf(noCode))))

        viewModel.onLocationChanged(LatLng(40.4406, -79.9959))
        advanceUntilIdle()

        assertEquals(listOf(WalkableStop("E12345", 120.0)), viewModel.state.value.walkableStops)
    }

    @Test
    fun onLocationChanged_queriesAroundLocationWith400mRadius() = runTest(dispatcher) {
        val source = FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS))
        val viewModel = MapViewModel(source)

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        assertEquals(listOf(LatLng(40.4443, -79.9532) to 400.0), source.queries)
    }

    @Test
    fun onLocationChanged_movedLessThan100m_doesNotQueryAgain() = runTest(dispatcher) {
        val source = FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS))
        val viewModel = MapViewModel(source)

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()
        // 0.0008° of latitude is about 89 m.
        viewModel.onLocationChanged(LatLng(40.4451, -79.9532))
        advanceUntilIdle()

        assertEquals(1, source.queries.size)
    }

    @Test
    fun onLocationChanged_movedMoreThan100m_queriesNewLocation() = runTest(dispatcher) {
        val source = FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS))
        val viewModel = MapViewModel(source)

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()
        // 0.0010° of latitude is about 111 m.
        viewModel.onLocationChanged(LatLng(40.4453, -79.9532))
        advanceUntilIdle()

        assertEquals(
            listOf(LatLng(40.4443, -79.9532) to 400.0, LatLng(40.4453, -79.9532) to 400.0),
            source.queries
        )
    }

    @Test
    fun onLocationChanged_smallMovesAddingUpPast100m_queriesOnceFarFromLastQuery() =
        runTest(dispatcher) {
            val source = FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS))
            val viewModel = MapViewModel(source)

            // Steps of about 44 m: 44 m and 89 m from the first query stay below 100 m, 133 m does not.
            viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
            advanceUntilIdle()
            viewModel.onLocationChanged(LatLng(40.4447, -79.9532))
            advanceUntilIdle()
            viewModel.onLocationChanged(LatLng(40.4451, -79.9532))
            advanceUntilIdle()
            viewModel.onLocationChanged(LatLng(40.4455, -79.9532))
            advanceUntilIdle()

            assertEquals(
                listOf(LatLng(40.4443, -79.9532) to 400.0, LatLng(40.4455, -79.9532) to 400.0),
                source.queries
            )
        }

    @Test
    fun onLocationChanged_lookupFails_reportsFailureAndKeepsPreviousMarkers() =
        runTest(dispatcher) {
            val source = FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS))
            val viewModel = MapViewModel(source)
            viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
            advanceUntilIdle()

            source.result = NearbyStopsResult.Failure(GtfsImportError.Network(NETWORK_DOWN))
            viewModel.onLocationChanged(LatLng(40.4453, -79.9532))
            advanceUntilIdle()

            assertEquals(
                MapUiState(
                    stopMarkers = listOf(
                        StopMarker(
                            "2635",
                            "FIFTH AVE + BELLEFIELD, OPP",
                            LatLng(40.445770, -79.951416)
                        ),
                        StopMarker(
                            "8312",
                            "FORBES AVE + MOREWOOD AVE",
                            LatLng(40.444557, -79.942791)
                        )
                    ),
                    stopsStatus = StopsStatus.Failed(GtfsImportError.Network(NETWORK_DOWN)),
                    walkableStops = listOf(
                        WalkableStop("2635", 218.0),
                        WalkableStop("8312", 880.0)
                    )
                ),
                viewModel.state.value
            )
        }

    @Test
    fun onLocationChanged_afterFailure_retriesEvenWithoutMoving() = runTest(dispatcher) {
        val source = FakeStopSource(NearbyStopsResult.Failure(GtfsImportError.Timeout))
        val viewModel = MapViewModel(source)
        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        source.result = NearbyStopsResult.Success(OAKLAND_STOPS)
        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        assertEquals(StopsStatus.Ready, viewModel.state.value.stopsStatus)
    }

    @Test
    fun onLocationChanged_timetableEndedYesterday_reportsItsLastDay() = runTest(dispatcher) {
        val viewModel = MapViewModel(
            FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS)),
            timetableDates = TimetableDatesSource {
                LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 10, 3)
            },
            clock = CLOCK
        )

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 10, 3), viewModel.state.value.timetableEndedOn)
    }

    @Test
    fun onLocationChanged_timetableRunsThroughToday_reportsNoEnd() = runTest(dispatcher) {
        val viewModel = MapViewModel(
            FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS)),
            timetableDates = TimetableDatesSource {
                LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 10, 4)
            },
            clock = CLOCK
        )

        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        assertNull(viewModel.state.value.timetableEndedOn)
    }

    @Test
    fun timetableUpdated_withNewerTimetable_clearsTheEndedWarning() = runTest(dispatcher) {
        var dates = LocalDate.of(2026, 6, 28)..LocalDate.of(2026, 10, 3)
        val imports = MutableStateFlow(Instant.parse("2026-09-20T12:00:00Z"))
        val viewModel = MapViewModel(
            FakeStopSource(NearbyStopsResult.Success(OAKLAND_STOPS)),
            timetableDates = TimetableDatesSource { dates },
            clock = CLOCK,
            timetableUpdates = imports
        )
        viewModel.onLocationChanged(LatLng(40.4443, -79.9532))
        advanceUntilIdle()

        dates = LocalDate.of(2026, 10, 4)..LocalDate.of(2027, 1, 9)
        imports.value = Instant.parse("2026-10-04T15:00:00Z")
        advanceUntilIdle()

        assertNull(viewModel.state.value.timetableEndedOn)
    }

    /** Answers every lookup with [result] and records the requested centers and radii. */
    private class FakeStopSource(var result: NearbyStopsResult) : NearbyStopSource {
        val queries = mutableListOf<Pair<LatLng, Double>>()

        override suspend fun nearbyStops(center: LatLng, radiusMeters: Double): NearbyStopsResult {
            queries.add(center to radiusMeters)
            return result
        }
    }

    private companion object {
        // 12:00 on Sunday 2026-10-04 in Pittsburgh (EDT, UTC-4).
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-10-04T16:00:00Z"), ZoneOffset.UTC)

        val NETWORK_DOWN = IOException("no network")

        val OAKLAND_STOPS = listOf(
            NearbyStop(
                StopEntity("2635", "2635", "FIFTH AVE + BELLEFIELD, OPP", 40.445770, -79.951416, 0),
                distanceMeters = 218.0
            ),
            NearbyStop(
                StopEntity("8312", "8312", "FORBES AVE + MOREWOOD AVE", 40.444557, -79.942791, 0),
                distanceMeters = 880.0
            )
        )
    }
}
