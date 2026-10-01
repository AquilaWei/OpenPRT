package org.openprt.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.openprt.app.geo.LatLng

/** Runtime permissions that let [FusedLocationProvider] work; either one is enough. */
val LOCATION_PERMISSIONS =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

/** True when the user has granted fine or coarse (approximate) location. */
fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * [LocationProvider] backed by Google Play services' fused location. Requires Play services on
 * the device; without it the request fails with [LocationError.Failed].
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    // await(CancellationTokenSource) is still marked experimental in kotlinx-coroutines-play-services.
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun currentLocation(): LocationResult {
        if (!hasLocationPermission(context)) {
            return LocationResult.Failure(LocationError.PermissionMissing)
        }
        // With only coarse permission granted the system lowers the accuracy on its own.
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()
        // Passing the source to await() cancels the Play services request with the coroutine.
        val cancellation = CancellationTokenSource()
        return try {
            val location = client.getCurrentLocation(
                request,
                cancellation.token
            ).await(cancellation)
            if (location == null) {
                LocationResult.Failure(LocationError.Unavailable)
            } else {
                LocationResult.Success(LatLng(location.latitude, location.longitude))
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the request.
            LocationResult.Failure(LocationError.PermissionMissing)
        } catch (e: ApiException) {
            LocationResult.Failure(LocationError.Failed(e))
        }
    }

    override fun locationUpdates(): Flow<LatLng> = callbackFlow {
        if (!hasLocationPermission(context)) {
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.lastLocation?.let { trySend(LatLng(it.latitude, it.longitude)) }
            }
        }
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Permission revoked between the check above and the request.
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 10_000L

        // Smaller moves cannot change which stops are nearby, so skip them to save battery.
        const val MIN_UPDATE_DISTANCE_METERS = 20f
    }
}
