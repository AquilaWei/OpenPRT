package org.openprt.app.data.gtfs

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.buffer
import okio.sink

/** Outcome of a GTFS import; failures are values so callers decide how to report them. */
sealed interface GtfsImportResult {
    data class Success(
        val stopCount: Int,
        val routeCount: Int,
        val tripCount: Int,
        val stopTimeCount: Int
    ) : GtfsImportResult

    data class Failure(val error: GtfsImportError) : GtfsImportResult
}

/** Why an import stored nothing. In every case the database still holds the previous feed. */
sealed interface GtfsImportError {
    /** The server answered with a non-2xx HTTP status. */
    data class Http(val code: Int) : GtfsImportError

    /** The download did not complete within the client's timeout. */
    data object Timeout : GtfsImportError

    /**
     * The download failed or was cut off (no network, DNS, connection reset), or the zip could
     * not be written to [GtfsImporter]'s download directory.
     */
    data class Network(val cause: IOException) : GtfsImportError

    /**
     * The download was not a usable GTFS zip, e.g. a missing or empty file or a duplicate stop
     * ID.
     */
    data class MalformedFeed(val cause: Exception) : GtfsImportError
}

/**
 * Downloads the PRT GTFS zip and replaces everything in [database] with its contents: stops,
 * routes, trips, stop times and service calendars.
 *
 * The zip is saved to a temporary file in [downloadDir] (about 22 MB, deleted afterwards) so a
 * slow or failed download never holds the database open. The tables are then emptied and
 * refilled in one transaction, streaming rows in batches because stop_times.txt is about 80 MB
 * of text. A broken feed rolls the transaction back, leaving the previous data untouched.
 */
class GtfsImporter(
    private val database: GtfsDatabase,
    private val downloadDir: File,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val feedUrl: HttpUrl = DEFAULT_FEED_URL.toHttpUrl(),
    // The download and the zip are read with blocking I/O.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** @throws IOException if the temporary file cannot be created in [downloadDir]. */
    suspend fun import(): GtfsImportResult = withContext(ioDispatcher) {
        val zipFile = File.createTempFile("gtfs", ".zip", downloadDir)
        try {
            val downloadError = download(zipFile)
            if (downloadError != null) GtfsImportResult.Failure(downloadError) else store(zipFile)
        } finally {
            zipFile.delete()
        }
    }

    private suspend fun download(target: File): GtfsImportError? = try {
        httpClient.newCall(Request.Builder().url(feedUrl).build()).executeAsync().use { response ->
            if (!response.isSuccessful) return GtfsImportError.Http(response.code)
            target.sink().buffer().use { it.writeAll(response.body.source()) }
        }
        null
    } catch (e: InterruptedIOException) {
        // OkHttp signals both socket and call timeouts with InterruptedIOException.
        GtfsImportError.Timeout
    } catch (e: IOException) {
        GtfsImportError.Network(e)
    }

    private suspend fun store(zipFile: File): GtfsImportResult = try {
        ZipFile(zipFile).use { zip -> database.withTransaction { replaceAll(zip) } }
    } catch (e: GtfsFormatException) {
        GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
    } catch (e: ZipException) {
        GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
    } catch (e: SQLiteConstraintException) {
        // A repeated ID, e.g. two stops with one stop_id.
        GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
    }

    /** Must run inside a transaction: it empties the tables before reading the new feed. */
    private suspend fun replaceAll(zip: ZipFile): GtfsImportResult.Success {
        val dao = database.gtfsDao()
        dao.deleteAll()
        val stopCount = zip.insertTable("stops.txt", GtfsRow::toStopEntity) { dao.insertStops(it) }
        val routeCount =
            zip.insertTable("routes.txt", GtfsRow::toRouteEntity) { dao.insertRoutes(it) }
        val tripCount = zip.insertTable("trips.txt", GtfsRow::toTripEntity) { dao.insertTrips(it) }
        val stopTimeCount =
            zip.insertTable("stop_times.txt", GtfsRow::toStopTimeEntity) { dao.insertStopTimes(it) }
        // GTFS requires at least one of the two calendar files.
        val calendarCount = zip.insertTable("calendar.txt", GtfsRow::toServiceCalendarEntity) {
            dao.insertCalendars(it)
        }
        val calendarDateCount =
            zip.insertTable("calendar_dates.txt", GtfsRow::toCalendarDateEntity) {
                dao.insertCalendarDates(it)
            }
        // A file with only its header row would replace a working timetable with nothing, so
        // empty tables are as broken as missing ones; throwing rolls the transaction back.
        if ((calendarCount ?: 0) + (calendarDateCount ?: 0) == 0) {
            throw GtfsFormatException("calendar.txt and calendar_dates.txt missing or empty")
        }
        return GtfsImportResult.Success(
            stopCount = requireRows("stops.txt", stopCount),
            routeCount = requireRows("routes.txt", routeCount),
            tripCount = requireRows("trips.txt", tripCount),
            stopTimeCount = requireRows("stop_times.txt", stopTimeCount)
        )
    }

    private fun requireRows(name: String, count: Int?): Int = when (count) {
        null -> throw GtfsFormatException("$name missing")
        0 -> throw GtfsFormatException("$name has no rows")
        else -> count
    }

    companion object {
        /** Linked from https://www.rideprt.org/business-center/developer-resources/ */
        const val DEFAULT_FEED_URL = "https://www.rideprt.org/developerresources/GTFS.zip"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient
            .Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}

private const val INSERT_BATCH_SIZE = 1000

/**
 * Parses [name] and hands its rows to [insert] in batches, so a large table is never
 * held in memory whole. Rows [parse] maps to `null` are skipped.
 *
 * @return the number of rows inserted, or `null` if the zip has no such file.
 */
private inline fun <T : Any> ZipFile.insertTable(
    name: String,
    noinline parse: (GtfsRow) -> T?,
    insert: (List<T>) -> Unit
): Int? {
    val entry = getEntry(name) ?: return null
    var count = 0
    getInputStream(entry).reader(Charsets.UTF_8).use { reader ->
        readGtfsTable(reader).mapNotNull(parse).chunked(INSERT_BATCH_SIZE).forEach {
            insert(it)
            count += it.size
        }
    }
    return count
}
