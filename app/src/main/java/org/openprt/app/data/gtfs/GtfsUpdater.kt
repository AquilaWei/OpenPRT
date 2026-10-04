package org.openprt.app.data.gtfs

import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * When the GTFS data on the device was last imported, kept in [prefs]. Stored outside the
 * database so recording it needs no schema change; a crash between the import and the record
 * only makes the data look older than it is, which costs one extra download.
 */
class GtfsImportLog(private val prefs: SharedPreferences) {
    /** Null when no import has been recorded, including imports by versions before 0.1.31. */
    val lastImport: Instant?
        get() {
            val millis = prefs.getLong(KEY_LAST_IMPORT, NONE)
            return if (millis == NONE) null else Instant.ofEpochMilli(millis)
        }

    fun record(at: Instant) {
        prefs.edit { putLong(KEY_LAST_IMPORT, at.toEpochMilli()) }
    }

    companion object {
        /** Name of the SharedPreferences file the app passes in. */
        const val PREFS_NAME = "gtfs"

        private const val KEY_LAST_IMPORT = "last_import_epoch_millis"
        private const val NONE = Long.MIN_VALUE
    }
}

/**
 * The one way the app imports GTFS data, so the first import at launch and the weekly background
 * update share one lock: when both start together, the second finds the data in place and does
 * not download it again.
 */
class GtfsUpdater(
    private val dao: GtfsDao,
    private val importer: GtfsImporter,
    private val log: GtfsImportLog,
    private val clock: Clock = Clock.systemUTC()
) {
    private val lock = Mutex()

    /** When the data was last imported; changes after every successful import. */
    val lastImport: Instant? get() = log.lastImport

    /**
     * Imports the feed when the database has no stops (first launch, or after a schema change
     * dropped the tables). Takes as long as a full download in that case.
     *
     * @return null when there was data already, otherwise the import's result.
     * @throws java.io.IOException as [GtfsImporter.import] does.
     */
    suspend fun importIfEmpty(): GtfsImportResult? = lock.withLock {
        if (dao.countStops() > 0) null else importAndRecord()
    }

    /**
     * Imports the feed again when the last recorded import is [maxAge] old or more, or was
     * never recorded. A failed import leaves the previous data and its import time in place.
     *
     * @return null when the data was recent enough, otherwise the import's result.
     * @throws java.io.IOException as [GtfsImporter.import] does.
     */
    suspend fun updateIfOlderThan(maxAge: Duration = MAX_AGE): GtfsImportResult? = lock.withLock {
        val last = log.lastImport
        if (last != null && Duration.between(last, clock.instant()) < maxAge) {
            null
        } else {
            importAndRecord()
        }
    }

    private suspend fun importAndRecord(): GtfsImportResult = importer.import().also {
        if (it is GtfsImportResult.Success) log.record(clock.instant())
    }

    companion object {
        /** How old the data may get before the background update downloads the feed again. */
        val MAX_AGE: Duration = Duration.ofDays(7)
    }
}
