package org.openprt.app.destination

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openprt.app.geo.BoundingBox
import org.openprt.app.geo.LatLng
import org.openprt.app.geo.PITTSBURGH_AREA

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationViewModelTest {
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
    fun onQueryChanged_before300ms_doesNotSearch() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)

        viewModel.onQueryChanged("cmu")
        advanceTimeBy(299)
        runCurrent()

        assertEquals(emptyList<String>(), geocoder.queries)
    }

    @Test
    fun onQueryChanged_after300ms_searchesOnce() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)

        viewModel.onQueryChanged("cmu")
        advanceTimeBy(300)
        runCurrent()

        assertEquals(listOf("cmu"), geocoder.queries)
    }

    @Test
    fun onQueryChanged_typedFasterThanDebounce_searchesOnlyLastQuery() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)

        viewModel.onQueryChanged("c")
        advanceTimeBy(200)
        viewModel.onQueryChanged("cm")
        advanceTimeBy(200)
        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        assertEquals(listOf("cmu"), geocoder.queries)
    }

    @Test
    fun onQueryChanged_whileWaiting_showsSearching() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder(GeocodeResult.Success(listOf(CMU))))

        viewModel.onQueryChanged("cmu")

        assertEquals(SearchStatus.Searching, viewModel.state.value.search)
    }

    @Test
    fun onQueryChanged_afterSearch_showsResults() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder(GeocodeResult.Success(listOf(CMU))))

        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        assertEquals(
            DestinationUiState(query = "cmu", search = SearchStatus.Results(listOf(CMU))),
            viewModel.state.value
        )
    }

    @Test
    fun onQueryChanged_resultsOutsidePittsburgh_areDropped() = runTest(dispatcher) {
        val philadelphia = Place("Carnegie Hall", "Philadelphia", LatLng(39.952583, -75.165222))
        val viewModel = DestinationViewModel(
            FakeGeocoder(GeocodeResult.Success(listOf(philadelphia, CMU)))
        )

        viewModel.onQueryChanged("carnegie")
        advanceUntilIdle()

        assertEquals(SearchStatus.Results(listOf(CMU)), viewModel.state.value.search)
    }

    @Test
    fun onQueryChanged_asksGeocoderForPittsburghArea() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)

        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        assertEquals(listOf(PITTSBURGH_AREA), geocoder.bounds)
    }

    @Test
    fun onQueryChanged_blank_clearsResultsWithoutSearching() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)
        viewModel.onQueryChanged("cmu")

        viewModel.onQueryChanged("  ")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), geocoder.queries)
        assertEquals(SearchStatus.Idle, viewModel.state.value.search)
    }

    @Test
    fun onQueryChanged_geocoderFails_showsError() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder(GeocodeResult.Failure(TIMEOUT)))

        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        assertEquals(SearchStatus.Failed(TIMEOUT), viewModel.state.value.search)
    }

    @Test
    fun retry_afterFailure_searchesAgainWithoutDebounce() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(
            GeocodeResult.Failure(TIMEOUT),
            GeocodeResult.Success(listOf(CMU))
        )
        val viewModel = DestinationViewModel(geocoder)
        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        viewModel.retry()
        runCurrent()

        assertEquals(listOf("cmu", "cmu"), geocoder.queries)
        assertEquals(SearchStatus.Results(listOf(CMU)), viewModel.state.value.search)
    }

    @Test
    fun onMapLongPress_setsPinnedDestination() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())

        viewModel.onMapLongPress(LatLng(40.4612, -79.9254))

        assertEquals(
            Destination(name = null, LatLng(40.4612, -79.9254)),
            viewModel.state.value.destination
        )
    }

    @Test
    fun onMapLongPress_whileSearching_cancelsSearch() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(listOf(CMU)))
        val viewModel = DestinationViewModel(geocoder)
        viewModel.onQueryChanged("cmu")

        viewModel.onMapLongPress(LatLng(40.4612, -79.9254))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), geocoder.queries)
        assertEquals(
            DestinationUiState(destination = Destination(null, LatLng(40.4612, -79.9254))),
            viewModel.state.value
        )
    }

    @Test
    fun selectPlace_setsNamedDestinationAndClosesSearch() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder(GeocodeResult.Success(listOf(CMU))))
        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        viewModel.selectPlace(CMU)

        assertEquals(
            DestinationUiState(
                destination = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    @Test
    fun onQueryChanged_beforeLocationIsKnown_searchesNearDowntown() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(emptyList()))
        val viewModel = DestinationViewModel(geocoder)

        viewModel.onQueryChanged("first baptist church")
        advanceUntilIdle()

        assertEquals(listOf(LatLng(40.4406, -79.9959)), geocoder.nearPoints)
    }

    @Test
    fun onQueryChanged_afterLocationIsKnown_searchesNearTheUser() = runTest(dispatcher) {
        val geocoder = FakeGeocoder(GeocodeResult.Success(emptyList()))
        val viewModel = DestinationViewModel(geocoder)
        viewModel.onLocationChanged(LatLng(40.4433, -79.9436))

        viewModel.onQueryChanged("first baptist church")
        advanceUntilIdle()

        assertEquals(listOf(LatLng(40.4433, -79.9436)), geocoder.nearPoints)
    }

    @Test
    fun selectPlace_namedBuildingWithAddress_labelsDestinationWithBoth() = runTest(dispatcher) {
        val cathedral = Place(
            "Cathedral of Learning",
            "4200 Fifth Avenue, Oakland, Pittsburgh",
            LatLng(40.4443, -79.9532),
            address = "4200 Fifth Avenue"
        )
        val viewModel = DestinationViewModel(FakeGeocoder())

        viewModel.selectPlace(cathedral)

        assertEquals(
            "Cathedral of Learning · 4200 Fifth Avenue",
            viewModel.state.value.destination?.name
        )
    }

    @Test
    fun selectPlace_whileRequestInFlight_ignoresItsLateResult() = runTest(dispatcher) {
        val answer = CompletableDeferred<GeocodeResult>()
        val viewModel = DestinationViewModel(geocoder = { _, _, _ -> answer.await() })
        viewModel.onQueryChanged("cmu")
        advanceUntilIdle()

        viewModel.selectPlace(CMU)
        answer.complete(GeocodeResult.Success(listOf(CMU)))
        advanceUntilIdle()

        assertEquals(SearchStatus.Idle, viewModel.state.value.search)
    }

    @Test
    fun clearDestination_removesDestination() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.onMapLongPress(LatLng(40.4612, -79.9254))

        viewModel.clearDestination()

        assertEquals(null, viewModel.state.value.destination)
    }

    /** Answers with [results] in order, repeating the last; records what it was asked. */
    @Test
    fun editOrigin_thenPlaceSelected_setsOriginAndKeepsDestination() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.onMapLongPress(PINNED)

        viewModel.editOrigin()
        viewModel.selectPlace(CMU)

        assertEquals(
            DestinationUiState(
                destination = Destination(null, PINNED),
                origin = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    @Test
    fun editOrigin_thenMapLongPressed_setsPinnedOrigin() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.selectPlace(CMU)

        viewModel.editOrigin()
        viewModel.onMapLongPress(PINNED)

        assertEquals(Destination(null, PINNED), viewModel.state.value.origin)
    }

    @Test
    fun editOrigin_noDestinationYetMapLongPressed_setsOriginOnly() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())

        viewModel.editOrigin()
        viewModel.onMapLongPress(PINNED)

        assertEquals(DestinationUiState(origin = Destination(null, PINNED)), viewModel.state.value)
    }

    @Test
    fun editOrigin_searchesForOrigin() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())

        viewModel.editOrigin()

        assertEquals(Endpoint.ORIGIN, viewModel.state.value.editing)
    }

    @Test
    fun selectPlace_afterOriginChosen_goesBackToSearchingDestinations() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.editOrigin()
        viewModel.selectPlace(CMU)

        viewModel.onMapLongPress(PINNED)

        assertEquals(Destination(null, PINNED), viewModel.state.value.destination)
    }

    @Test
    fun cancelOriginEdit_keepsOriginAndSearchesDestinationsAgain() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.editOrigin()
        viewModel.selectPlace(CMU)
        viewModel.editOrigin()
        viewModel.onQueryChanged("pit")

        viewModel.cancelOriginEdit()

        assertEquals(
            DestinationUiState(origin = Destination("Carnegie Mellon University", CMU.location)),
            viewModel.state.value
        )
    }

    @Test
    fun clearOrigin_startsFromUserLocationAgain() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.editOrigin()
        viewModel.selectPlace(CMU)

        viewModel.clearOrigin()

        assertEquals(null, viewModel.state.value.origin)
    }

    @Test
    fun swapEndpoints_fromUserLocation_destinationBecomesWhereUserIs() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.onLocationChanged(USER)
        viewModel.selectPlace(CMU)

        viewModel.swapEndpoints()

        assertEquals(
            DestinationUiState(
                destination = Destination(null, USER, wasUserLocation = true),
                origin = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    @Test
    fun swapEndpoints_chosenOrigin_exchangesBothEnds() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.selectPlace(CMU)
        viewModel.editOrigin()
        viewModel.onMapLongPress(PINNED)

        viewModel.swapEndpoints()

        assertEquals(
            DestinationUiState(
                destination = Destination(null, PINNED),
                origin = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    @Test
    fun swapEndpoints_twiceFromUserLocation_startsFromUserLocationAgain() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.onLocationChanged(USER)
        viewModel.selectPlace(CMU)

        viewModel.swapEndpoints()
        viewModel.swapEndpoints()

        assertEquals(
            DestinationUiState(
                destination = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    @Test
    fun swapEndpoints_userLocationUnknown_changesNothing() = runTest(dispatcher) {
        val viewModel = DestinationViewModel(FakeGeocoder())
        viewModel.selectPlace(CMU)

        viewModel.swapEndpoints()

        assertEquals(
            DestinationUiState(
                destination = Destination("Carnegie Mellon University", CMU.location)
            ),
            viewModel.state.value
        )
    }

    private class FakeGeocoder(vararg results: GeocodeResult) : Geocoder {
        private val remaining = ArrayDeque(results.toList())
        val queries = mutableListOf<String>()
        val bounds = mutableListOf<BoundingBox>()
        val nearPoints = mutableListOf<LatLng>()

        override suspend fun search(
            query: String,
            bounds: BoundingBox,
            near: LatLng
        ): GeocodeResult {
            queries += query
            this.bounds += bounds
            nearPoints += near
            return if (remaining.size > 1) remaining.removeFirst() else remaining.first()
        }
    }

    private companion object {
        val CMU = Place(
            "Carnegie Mellon University",
            "North Oakland, Pittsburgh",
            LatLng(40.4439193, -79.9428267)
        )
        val TIMEOUT = GeocodeError.Timeout
        val PINNED = LatLng(40.4612, -79.9254)
        val USER = LatLng(40.4443, -79.9532)
    }
}
