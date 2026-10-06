package org.openprt.app.walk

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import org.openprt.app.geo.LatLng

/**
 * Walking routes from a Valhalla server with the `pedestrian` costing; by default the free FOSSGIS
 * one on OpenStreetMap data, which needs no key. Its usage policy asks for a real User-Agent, at
 * most one request a second and no heavy use, so requests from this instance are spaced
 * [minInterval] apart (a caller asking for several walks at once waits) and callers should cache
 * results, e.g. with [CachingWalkRouter].
 *
 * Any failure, including no answer within [DEFAULT_TIMEOUT_SECONDS], gives [WalkPath.Straight].
 * A street route is drawn from exactly [route]'s `from` to its `to`, joined to where the server
 * snapped them onto the street.
 */
class ValhallaWalkRouter(
    /** Sent with every request, as the usage policy requires. */
    private val userAgent: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val minInterval: Duration = 1.seconds,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    // Response bodies are read with blocking I/O.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WalkRouter {
    private val json = Json { ignoreUnknownKeys = true }

    // Held for the whole request, so two callers can never be closer than minInterval.
    private val spacing = Mutex()
    private var lastRequest: TimeMark? = null

    override suspend fun route(from: LatLng, to: LatLng): WalkPath {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addQueryParameter("json", query(from, to)).build())
            .header("User-Agent", userAgent)
            .build()
        val straight = WalkPath.Straight(from, to)
        val body = spacing.withLock {
            lastRequest?.let { delay(minInterval - it.elapsedNow()) }
            lastRequest = timeSource.markNow()
            try {
                withContext(ioDispatcher) {
                    httpClient.newCall(request).executeAsync().use { response ->
                        if (response.isSuccessful) response.body.string() else null
                    }
                }
            } catch (e: IOException) {
                // Timeouts are InterruptedIOException, so they land here too.
                null
            }
        } ?: return straight
        return decode(body, from, to) ?: straight
    }

    private fun query(from: LatLng, to: LatLng): String = buildJsonObject {
        putJsonArray("locations") {
            addJsonObject {
                put("lat", from.latitude)
                put("lon", from.longitude)
            }
            addJsonObject {
                put("lat", to.latitude)
                put("lon", to.longitude)
            }
        }
        put("costing", "pedestrian")
        // Only the line and the time are used; turn-by-turn text would just add bytes.
        put("directions_type", "none")
    }.toString()

    /** Null when the body is not a route with a drawable line. */
    private fun decode(body: String, from: LatLng, to: LatLng): WalkPath.Streets? {
        val trip: TripDto
        val points: List<LatLng>
        try {
            trip = json.decodeFromString(RouteDto.serializer(), body).trip
            points = trip.legs.flatMap { decodePolyline6(it.shape) }
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            // Also covers JSON that is not an object at all, and a cut-off shape.
            return null
        }
        if (points.size < 2) return null
        return WalkPath.Streets(listOf(from) + points + to, ceil(trip.summary.time).toLong())
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://valhalla1.openstreetmap.de/route"

        /** A slow walk route should not hold up the plan's map for long. */
        const val DEFAULT_TIMEOUT_SECONDS = 5L

        fun defaultHttpClient(): OkHttpClient = OkHttpClient
            .Builder()
            .callTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private class RouteDto(val trip: TripDto)

@Serializable
private class TripDto(val legs: List<LegDto>, val summary: SummaryDto)

@Serializable
private class LegDto(val shape: String)

/** [time] is in seconds. */
@Serializable
private class SummaryDto(val time: Double)

/**
 * Decodes Google's encoded polyline format at Valhalla's precision of six decimal places.
 *
 * @throws IllegalArgumentException if [encoded] ends in the middle of a value.
 */
internal fun decodePolyline6(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var latitude = 0L
    var longitude = 0L

    fun nextValue(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(index < encoded.length) { "polyline ends mid-value: $encoded" }
            val chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f).toLong() shl shift)
            shift += 5
            if (chunk < 0x20) break
        }
        // Zig-zag: the lowest bit is the sign.
        return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    }

    while (index < encoded.length) {
        latitude += nextValue()
        longitude += nextValue()
        points += LatLng(latitude / 1e6, longitude / 1e6)
    }
    return points
}
