package org.openprt.app.data.truetime

import java.io.IOException
import java.io.InterruptedIOException
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/**
 * Client for PRT TrueTime, a Clever Devices BusTime v3 API.
 *
 * Every call returns a [TrueTimeResult]; HTTP, network, API and parsing failures are
 * [TrueTimeResult.Failure], never exceptions. Only invalid arguments (e.g. too many IDs) throw
 * [IllegalArgumentException]. With a blank [apiKey] every call returns
 * [TrueTimeError.MissingApiKey] without touching the network.
 */
class TrueTimeClient(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    // Response bodies are read with blocking I/O.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun getRoutes(): TrueTimeResult<List<Route>> =
        request("getroutes", emptyMap(), "routes", RouteDto.serializer()) {
            it.toModel()
        }

    suspend fun getDirections(routeId: String): TrueTimeResult<List<Direction>> =
        request("getdirections", mapOf("rt" to routeId), "directions", DirectionDto.serializer()) {
            it.toModel()
        }

    /** [directionId] is a [Direction.id] from [getDirections]. */
    suspend fun getStops(routeId: String, directionId: String): TrueTimeResult<List<Stop>> =
        request(
            "getstops",
            mapOf("rt" to routeId, "dir" to directionId),
            "stops",
            StopDto.serializer()
        ) {
            it.toModel()
        }

    /**
     * Upcoming arrivals at up to [MAX_IDS_PER_CALL] stops. If only some stops have no data the
     * API still returns predictions for the others, and so does this call.
     */
    suspend fun getPredictions(stopIds: List<String>): TrueTimeResult<List<Prediction>> {
        require(stopIds.size in 1..MAX_IDS_PER_CALL) {
            "getpredictions takes 1..$MAX_IDS_PER_CALL stop IDs"
        }
        return request(
            "getpredictions",
            mapOf("stpid" to stopIds.joinToString(",")),
            "prd",
            PredictionDto.serializer()
        ) {
            it.toModel()
        }
    }

    /** Latest positions of up to [MAX_IDS_PER_CALL] vehicles. */
    suspend fun getVehicles(vehicleIds: List<String>): TrueTimeResult<List<Vehicle>> {
        require(vehicleIds.size in 1..MAX_IDS_PER_CALL) {
            "getvehicles takes 1..$MAX_IDS_PER_CALL vehicle IDs"
        }
        return request(
            "getvehicles",
            mapOf("vid" to vehicleIds.joinToString(",")),
            "vehicle",
            VehicleDto.serializer()
        ) {
            it.toModel()
        }
    }

    /** The shape and stops of pattern [patternId], e.g. a [Vehicle.patternId]. */
    suspend fun getPatterns(patternId: Int): TrueTimeResult<List<Pattern>> = request(
        "getpatterns",
        mapOf("pid" to patternId.toString()),
        "ptr",
        PatternDto.serializer()
    ) {
        it.toModel()
    }

    /**
     * Calls [endpoint] and decodes the list under `bustime-response.[dataKey]`.
     *
     * BusTime always answers 200 for API-level problems and reports them in
     * `bustime-response.error`; those become [TrueTimeError.Api] only when no data came back.
     */
    private suspend fun <D, T> request(
        endpoint: String,
        params: Map<String, String>,
        dataKey: String,
        serializer: KSerializer<D>,
        toModel: (D) -> T
    ): TrueTimeResult<List<T>> {
        if (apiKey.isBlank()) return TrueTimeResult.Failure(TrueTimeError.MissingApiKey)

        val url =
            baseUrl
                .newBuilder()
                .addPathSegment(endpoint)
                .addQueryParameter("key", apiKey)
                .addQueryParameter("format", "json")
                .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
                .build()

        val body =
            try {
                fetch(url)
            } catch (e: InterruptedIOException) {
                // OkHttp signals both socket and call timeouts with InterruptedIOException.
                return TrueTimeResult.Failure(TrueTimeError.Timeout)
            } catch (e: IOException) {
                return TrueTimeResult.Failure(TrueTimeError.Network(e))
            }

        return when (body) {
            is HttpBody.Error -> TrueTimeResult.Failure(TrueTimeError.Http(body.code))
            is HttpBody.Ok -> decode(body.text, dataKey, serializer, toModel)
        }
    }

    private suspend fun fetch(url: HttpUrl): HttpBody = withContext(ioDispatcher) {
        httpClient.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
            if (response.isSuccessful) {
                HttpBody.Ok(
                    response.body.string()
                )
            } else {
                HttpBody.Error(response.code)
            }
        }
    }

    private fun <D, T> decode(
        text: String,
        dataKey: String,
        serializer: KSerializer<D>,
        toModel: (D) -> T
    ): TrueTimeResult<List<T>> = try {
        val response = json.parseToJsonElement(
            text
        ).jsonObject.getValue("bustime-response").jsonObject
        val data = response[dataKey] as? JsonArray
        val errors = response["error"]?.let {
            json.decodeFromJsonElement(ListSerializer(ErrorDto.serializer()), it)
        }.orEmpty()
        if (data.isNullOrEmpty() && errors.isNotEmpty()) {
            TrueTimeResult.Failure(TrueTimeError.Api(errors.map { it.msg }))
        } else {
            val items = data?.let {
                json.decodeFromJsonElement(ListSerializer(serializer), it)
            }.orEmpty()
            TrueTimeResult.Success(items.map(toModel))
        }
    } catch (e: SerializationException) {
        TrueTimeResult.Failure(TrueTimeError.MalformedResponse(e))
    } catch (e: IllegalArgumentException) {
        // Also covers non-object JSON, a missing "bustime-response" and bad enum values.
        TrueTimeResult.Failure(TrueTimeError.MalformedResponse(e))
    } catch (e: NoSuchElementException) {
        TrueTimeResult.Failure(TrueTimeError.MalformedResponse(e))
    } catch (e: DateTimeParseException) {
        TrueTimeResult.Failure(TrueTimeError.MalformedResponse(e))
    }

    private sealed interface HttpBody {
        class Ok(val text: String) : HttpBody

        class Error(val code: Int) : HttpBody
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://truetime.rideprt.org/bustime/api/v3/"

        /** BusTime rejects more than this many stop or vehicle IDs in one request. */
        const val MAX_IDS_PER_CALL = 10

        fun defaultHttpClient(): OkHttpClient = OkHttpClient
            .Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
