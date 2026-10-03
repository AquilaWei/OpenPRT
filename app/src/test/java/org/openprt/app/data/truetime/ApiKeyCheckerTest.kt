package org.openprt.app.data.truetime

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiKeyCheckerTest {
    @Test
    fun toKeyCheck_success_isValid() {
        val result = TrueTimeResult.Success(emptyList<Route>())

        assertEquals(KeyCheck.Valid, result.toKeyCheck())
    }

    @Test
    fun toKeyCheck_invalidKeyMessage_isRejectedWithMessage() {
        // The message PRT returned for a made-up key on 2026-10-01.
        val result = TrueTimeResult.Failure(
            TrueTimeError.Api(listOf("Invalid API access key supplied"))
        )

        assertEquals(
            KeyCheck.Rejected(listOf("Invalid API access key supplied")),
            result.toKeyCheck()
        )
    }

    @Test
    fun toKeyCheck_apiErrorNotAboutKey_isUnreachable() {
        val error = TrueTimeError.Api(
            listOf("Transaction limit for current day has been exceeded.")
        )

        assertEquals(KeyCheck.Unreachable(error), TrueTimeResult.Failure(error).toKeyCheck())
    }

    @Test
    fun toKeyCheck_networkError_isUnreachable() {
        val error = TrueTimeError.Network(IOException("offline"))

        assertEquals(KeyCheck.Unreachable(error), TrueTimeResult.Failure(error).toKeyCheck())
    }

    @Test
    fun toKeyCheck_blankKey_isRejected() {
        val result = TrueTimeResult.Failure(TrueTimeError.MissingApiKey)

        assertEquals(KeyCheck.Rejected(emptyList()), result.toKeyCheck())
    }
}
