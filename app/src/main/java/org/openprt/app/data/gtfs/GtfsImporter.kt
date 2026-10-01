package org.openprt.app.data.gtfs

import android.database.sqlite.SQLiteConstraintException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/** Outcome of a GTFS import; failures are values so callers decide how to report them. */
sealed interface GtfsImportResult {
    data class Success(val stopCount: Int, val routeCount: Int) : GtfsImportResult

    data class Failure(val error: GtfsImportError) : GtfsImportResult
}

/** Why an import stored nothing. In every case the database still holds the previous feed. */
sealed interface GtfsImportError {
    /** The server answered with a non-2xx HTTP status. */
    data class Http(val code: Int) : GtfsImportError

    /** The download did not complete within the client's timeout. */
    data object Timeout : GtfsImportError

    /** The download failed or was cut off (no network, DNS, connection reset). */
    data class Network(val cause: IOException) : GtfsImportError

    /** The download was not a usable GTFS zip, e.g. a missing file or a duplicate stop ID. */
    data class MalformedFeed(val cause: Exception) : GtfsImportError
}

/**
 * Downloads the PRT GTFS zip and replaces the stops and routes in [dao] with its contents.
 *
 * The whole feed is parsed before anything is written, and the write is a single transaction,
 * so a failed download or a broken feed leaves the existing data untouched.
 */
class GtfsImporter(
    private val dao: GtfsDao,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val feedUrl: HttpUrl = DEFAULT_FEED_URL.toHttpUrl(),
    // The zip is read with blocking I/O.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun import(): GtfsImportResult {
        val feed =
            try {
                download()
            } catch (e: InterruptedIOException) {
                // OkHttp signals both socket and call timeouts with InterruptedIOException.
                return GtfsImportResult.Failure(GtfsImportError.Timeout)
            } catch (e: HttpStatusException) {
                return GtfsImportResult.Failure(GtfsImportError.Http(e.code))
            } catch (e: GtfsFormatException) {
                return GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
            } catch (e: ZipException) {
                return GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
            } catch (e: IOException) {
                return GtfsImportResult.Failure(GtfsImportError.Network(e))
            }

        try {
            dao.replaceAll(feed.stops.map { it.toEntity() }, feed.routes.map { it.toEntity() })
        } catch (e: SQLiteConstraintException) {
            return GtfsImportResult.Failure(GtfsImportError.MalformedFeed(e))
        }
        return GtfsImportResult.Success(stopCount = feed.stops.size, routeCount = feed.routes.size)
    }

    /** Parses while downloading, so the 20+ MB zip is never held in memory or on disk. */
    private suspend fun download(): GtfsFeed = withContext(ioDispatcher) {
        httpClient.newCall(Request.Builder().url(feedUrl).build()).executeAsync().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code)
            response.body.byteStream().use(::readGtfsFeed)
        }
    }

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        /** Linked from https://www.rideprt.org/business-center/developer-resources/ */
        const val DEFAULT_FEED_URL = "https://www.rideprt.org/developerresources/GTFS.zip"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient
            .Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}

private fun GtfsStop.toEntity() = StopEntity(
    stopId = id,
    code = code,
    name = name,
    latitude = latitude,
    longitude = longitude,
    locationType = locationType
)

private fun GtfsRoute.toEntity() = RouteEntity(
    routeId = id,
    shortName = shortName,
    longName = longName,
    type = type,
    color = color
)
