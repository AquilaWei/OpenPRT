package org.openprt.app.data.gtfs

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * [NearbyStopSource] over the GTFS database that imports the feed first when the database has
 * no stops (first launch, or after a schema change dropped the tables). The first lookup can
 * therefore take as long as a full GTFS download.
 */
class NearbyStopRepository(
    private val dao: GtfsDao,
    private val importer: GtfsImporter,
    private val finder: NearbyStopFinder = NearbyStopFinder(dao)
) : NearbyStopSource {
    // Serializes the empty check and the import so overlapping lookups download only once.
    private val importLock = Mutex()

    override suspend fun nearbyStops(center: LatLng, radiusMeters: Double): NearbyStopsResult {
        val importFailure = importLock.withLock {
            if (dao.countStops() == 0) importer.import() as? GtfsImportResult.Failure else null
        }
        if (importFailure != null) return NearbyStopsResult.Failure(importFailure.error)
        return NearbyStopsResult.Success(finder.findNearby(center, radiusMeters))
    }
}
