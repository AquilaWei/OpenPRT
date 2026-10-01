package org.openprt.app.location

import org.openprt.app.geo.LatLng

/** Market Square, used as the map center whenever the device location is not available. */
val DOWNTOWN_PITTSBURGH = LatLng(latitude = 40.4406, longitude = -79.9959)

/**
 * Source of the device's current position, kept behind an interface so the ViewModel can be
 * tested with a fake and the Google Play implementation can be swapped (e.g. for iOS).
 */
interface LocationProvider {
    /**
     * A single current fix. Failures are returned as values; implementations do not apply their
     * own timeout and may suspend for a long time, so callers must bound the wait.
     */
    suspend fun currentLocation(): LocationResult
}

/** Outcome of one location request. */
sealed interface LocationResult {
    data class Success(val location: LatLng) : LocationResult

    data class Failure(val error: LocationError) : LocationResult
}

/** Why no location was obtained. */
sealed interface LocationError {
    /** Neither fine nor coarse location permission is granted (e.g. revoked while running). */
    data object PermissionMissing : LocationError

    /** The system had no fix to give, typically because location services are switched off. */
    data object Unavailable : LocationError

    /** No fix arrived within the caller's time limit. */
    data object Timeout : LocationError

    /** The location service reported an error, e.g. Google Play services missing or outdated. */
    data class Failed(val cause: Exception) : LocationError
}
