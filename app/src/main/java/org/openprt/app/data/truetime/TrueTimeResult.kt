package org.openprt.app.data.truetime

import java.io.IOException

/** Outcome of a TrueTime call; failures are values so callers must decide how to show them. */
sealed interface TrueTimeResult<out T> {
    data class Success<T>(val value: T) : TrueTimeResult<T>

    data class Failure(val error: TrueTimeError) : TrueTimeResult<Nothing>
}

/** Why a TrueTime call produced no data. */
sealed interface TrueTimeError {
    /** No PRT_API_KEY was configured at build time, so no request was sent. */
    data object MissingApiKey : TrueTimeError

    /**
     * The API answered with `bustime-response.error` and no data, e.g. "No data found for
     * parameter" or "Invalid API access key supplied". [messages] are the API's own `msg` texts.
     */
    data class Api(val messages: List<String>) : TrueTimeError

    /** The server answered with a non-2xx HTTP status. */
    data class Http(val code: Int) : TrueTimeError

    /** The request did not complete within the client's timeout. */
    data object Timeout : TrueTimeError

    /** The request failed before a response arrived (no network, DNS, connection reset). */
    data class Network(val cause: IOException) : TrueTimeError

    /** The response body was not the JSON shape BusTime v3 documents. */
    data class MalformedResponse(val cause: Exception) : TrueTimeError
}
