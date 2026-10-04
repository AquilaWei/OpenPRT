package org.openprt.app.data.gtfs

import org.openprt.app.geo.LatLng

/** Outcome of a nearby-stop lookup; failures are values so the map can show them. */
sealed interface NearbyStopsResult {
    data class Success(val stops: List<NearbyStop>) : NearbyStopsResult

    /** Stop data was missing and downloading it failed. */
    data class Failure(val error: GtfsImportError) : NearbyStopsResult
}

/** Where the map gets its stops; an interface so the ViewModel can be tested with a fake. */
fun interface NearbyStopSource {
    /** Stops within [radiusMeters] of [center], nearest first. */
    suspend fun nearbyStops(center: LatLng, radiusMeters: Double): NearbyStopsResult
}

/**
 * [NearbyStopSource] over the GTFS database that has [updater] import the feed first when the
 * database has no stops. The first lookup can therefore take as long as a full GTFS download.
 */
class NearbyStopRepository(
    private val dao: GtfsDao,
    private val updater: GtfsUpdater,
    private val finder: NearbyStopFinder = NearbyStopFinder(dao)
) : NearbyStopSource {
    override suspend fun nearbyStops(center: LatLng, radiusMeters: Double): NearbyStopsResult {
        val importFailure = updater.importIfEmpty() as? GtfsImportResult.Failure
        if (importFailure != null) return NearbyStopsResult.Failure(importFailure.error)
        return NearbyStopsResult.Success(finder.findNearby(center, radiusMeters))
    }
}
