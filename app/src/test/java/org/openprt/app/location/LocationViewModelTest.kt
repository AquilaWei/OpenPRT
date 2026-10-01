package org.openprt.app.location

import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
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
import org.openprt.app.geo.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {
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
    fun state_beforePermissionAnswer_isAwaitingPermission() {
        val viewModel = LocationViewModel(FakeLocationProvider(OAKLAND_FIX))

        assertEquals(LocationUiState.AwaitingPermission, viewModel.state.value)
    }

    @Test
    fun onPermissionResult_granted_usesReportedLocation() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(OAKLAND_FIX))

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(
            LocationUiState.Located(LatLng(40.4443, -79.9532)),
            viewModel.state.value
        )
    }

    @Test
    fun onPermissionResult_grantedAndFixPending_isLoading() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(result = null))

        viewModel.onPermissionResult(granted = true)
        runCurrent()

        assertEquals(LocationUiState.Loading, viewModel.state.value)
    }

    @Test
    fun onPermissionResult_denied_isPermissionDeniedAtDowntown() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(OAKLAND_FIX))

        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()

        assertEquals(
            LocationUiState.PermissionDenied(LatLng(40.4406, -79.9959)),
            viewModel.state.value
        )
    }

    @Test
    fun onPermissionResult_denied_doesNotQueryProvider() = runTest(dispatcher) {
        val provider = FakeLocationProvider(OAKLAND_FIX)
        val viewModel = LocationViewModel(provider)

        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()

        assertEquals(0, provider.calls)
    }

    @Test
    fun onPermissionResult_providerNeverAnswers_failsWithTimeoutAfterLimit() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(result = null), timeout = 10.seconds)

        viewModel.onPermissionResult(granted = true)
        advanceTimeBy(10.seconds)
        runCurrent()

        assertEquals(
            LocationUiState.Failed(LocationError.Timeout, LatLng(40.4406, -79.9959)),
            viewModel.state.value
        )
    }

    @Test
    fun onPermissionResult_providerSlowerThanTimeout_isStillLoadingJustBeforeLimit() =
        runTest(dispatcher) {
            val viewModel =
                LocationViewModel(FakeLocationProvider(result = null), timeout = 10.seconds)

            viewModel.onPermissionResult(granted = true)
            advanceTimeBy(9.seconds)
            runCurrent()

            assertEquals(LocationUiState.Loading, viewModel.state.value)
        }

    @Test
    fun onPermissionResult_locationTurnedOff_failsWithUnavailable() = runTest(dispatcher) {
        val viewModel = LocationViewModel(
            FakeLocationProvider(LocationResult.Failure(LocationError.Unavailable))
        )

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(
            LocationUiState.Failed(LocationError.Unavailable, LatLng(40.4406, -79.9959)),
            viewModel.state.value
        )
    }

    @Test
    fun onPermissionResult_serviceError_failsCarryingCause() = runTest(dispatcher) {
        val cause = IOException("play services down")
        val viewModel = LocationViewModel(
            FakeLocationProvider(LocationResult.Failure(LocationError.Failed(cause)))
        )

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(
            LocationUiState.Failed(LocationError.Failed(cause), LatLng(40.4406, -79.9959)),
            viewModel.state.value
        )
    }

    @Test
    fun onPermissionResult_permissionRevokedBeforeFix_isPermissionDenied() = runTest(dispatcher) {
        val viewModel = LocationViewModel(
            FakeLocationProvider(LocationResult.Failure(LocationError.PermissionMissing))
        )

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(
            LocationUiState.PermissionDenied(LatLng(40.4406, -79.9959)),
            viewModel.state.value
        )
    }

    @Test
    fun followLocation_afterFix_appliesLaterUpdates() = runTest(dispatcher) {
        val provider = FakeLocationProvider(OAKLAND_FIX)
        val viewModel = LocationViewModel(provider)
        val following = launch { viewModel.followLocation() }
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        provider.updates.emit(LatLng(40.4417, -79.9562))
        runCurrent()

        following.cancel()
        assertEquals(LocationUiState.Located(LatLng(40.4417, -79.9562)), viewModel.state.value)
    }

    @Test
    fun followLocation_afterFailedFix_updateTurnsStateIntoLocated() = runTest(dispatcher) {
        val provider = FakeLocationProvider(LocationResult.Failure(LocationError.Unavailable))
        val viewModel = LocationViewModel(provider)
        val following = launch { viewModel.followLocation() }
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        provider.updates.emit(LatLng(40.4417, -79.9562))
        runCurrent()

        following.cancel()
        assertEquals(LocationUiState.Located(LatLng(40.4417, -79.9562)), viewModel.state.value)
    }

    @Test
    fun followLocation_permissionDenied_doesNotListenForUpdates() = runTest(dispatcher) {
        val provider = FakeLocationProvider(OAKLAND_FIX)
        val viewModel = LocationViewModel(provider)
        val following = launch { viewModel.followLocation() }

        viewModel.onPermissionResult(granted = false)
        advanceUntilIdle()

        following.cancel()
        assertEquals(0, provider.updateSubscriptions)
    }

    @Test
    fun followLocation_cancelled_stopsListening() = runTest(dispatcher) {
        val provider = FakeLocationProvider(OAKLAND_FIX)
        val viewModel = LocationViewModel(provider)
        val following = launch { viewModel.followLocation() }
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        following.cancel()
        advanceUntilIdle()

        assertEquals(0, provider.updates.subscriptionCount.value)
    }

    @Test
    fun relocate_afterFix_returnsToAwaitingPermission() = runTest(dispatcher) {
        val viewModel = LocationViewModel(FakeLocationProvider(OAKLAND_FIX))
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        viewModel.relocate()

        assertEquals(LocationUiState.AwaitingPermission, viewModel.state.value)
    }

    @Test
    fun relocate_thenGranted_takesFreshFix() = runTest(dispatcher) {
        val provider = FakeLocationProvider(OAKLAND_FIX)
        val viewModel = LocationViewModel(provider)
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        viewModel.relocate()
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(2, provider.calls)
    }

    @Test
    fun location_whileLoading_isNull() {
        assertEquals(null, LocationUiState.Loading.location)
    }

    /**
     * Returns [result] on every call, or suspends until cancelled when [result] is null.
     * Continuous updates are whatever the test emits into [updates].
     */
    private class FakeLocationProvider(private val result: LocationResult?) : LocationProvider {
        var calls = 0
        var updateSubscriptions = 0
        val updates = MutableSharedFlow<LatLng>()

        override suspend fun currentLocation(): LocationResult {
            calls++
            return result ?: awaitCancellation()
        }

        override fun locationUpdates(): Flow<LatLng> = flow {
            updateSubscriptions++
            emitAll(updates)
        }
    }

    private companion object {
        val OAKLAND_FIX = LocationResult.Success(LatLng(40.4443, -79.9532))
    }
}
