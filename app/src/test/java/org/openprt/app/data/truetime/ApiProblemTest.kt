package org.openprt.app.data.truetime

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiProblemTest {
    @Test
    fun problem_invalidKeyMessage_isInvalidKey() {
        val error = TrueTimeError.Api(listOf("Invalid API access key supplied"))

        assertEquals(ApiProblem.INVALID_KEY, error.problem)
    }

    @Test
    fun problem_dailyTransactionLimitMessage_isQuotaExceeded() {
        val error = TrueTimeError.Api(
            listOf("Transaction limit for current day has been exceeded.")
        )

        assertEquals(ApiProblem.QUOTA_EXCEEDED, error.problem)
    }

    @Test
    fun problem_otherMessage_isOther() {
        val error = TrueTimeError.Api(listOf("Internal server error"))

        assertEquals(ApiProblem.OTHER, error.problem)
    }
}
