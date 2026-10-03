package org.openprt.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.openprt.app.R
import org.openprt.app.data.truetime.TrueTimeError

/**
 * Why a TrueTime request failed, in a few words for the end of an error message, so the user
 * can tell a dropped connection from a TrueTime problem.
 */
@Composable
fun trueTimeErrorReason(error: TrueTimeError): String = when (error) {
    TrueTimeError.MissingApiKey -> stringResource(R.string.error_reason_missing_key)

    // TrueTime's own text, e.g. "Invalid API access key supplied".
    is TrueTimeError.Api -> stringResource(
        R.string.error_reason_api,
        error.messages.joinToString("; ")
    )

    is TrueTimeError.Http -> stringResource(R.string.error_reason_http, error.code)

    TrueTimeError.Timeout -> stringResource(R.string.error_reason_timeout)

    is TrueTimeError.Network -> stringResource(R.string.error_reason_network)

    is TrueTimeError.MalformedResponse -> stringResource(R.string.error_reason_malformed)
}
