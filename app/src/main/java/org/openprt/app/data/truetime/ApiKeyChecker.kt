package org.openprt.app.data.truetime

/** What TrueTime said about a key the user entered. */
sealed interface KeyCheck {
    data object Valid : KeyCheck

    /** TrueTime refused the key; [messages] are its own texts, e.g. "Invalid API access key". */
    data class Rejected(val messages: List<String>) : KeyCheck

    /** TrueTime could not be asked (offline, timeout, server error), so the key is unknown. */
    data class Unreachable(val error: TrueTimeError) : KeyCheck
}

/** Asks TrueTime whether a key works; an interface so screens can be tested with a fake. */
fun interface ApiKeyChecker {
    suspend fun check(key: String): KeyCheck
}

/** Checks a key with one `getroutes` call, the cheapest endpoint that needs no parameters. */
fun TrueTimeClient.Companion.keyChecker(): ApiKeyChecker = ApiKeyChecker { key ->
    TrueTimeClient(apiKey = { key }).getRoutes().toKeyCheck()
}

/**
 * Only API errors that mention the key count as a rejection; other API errors, such as a
 * transaction limit, say nothing about the key and leave it unchecked.
 */
internal fun TrueTimeResult<*>.toKeyCheck(): KeyCheck = when (this) {
    is TrueTimeResult.Success -> KeyCheck.Valid

    is TrueTimeResult.Failure -> when (val error = error) {
        TrueTimeError.MissingApiKey -> KeyCheck.Rejected(emptyList())

        is TrueTimeError.Api ->
            if (error.messages.any { it.contains("key", ignoreCase = true) }) {
                KeyCheck.Rejected(error.messages)
            } else {
                KeyCheck.Unreachable(error)
            }

        else -> KeyCheck.Unreachable(error)
    }
}
