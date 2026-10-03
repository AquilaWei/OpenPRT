package org.openprt.app.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.openprt.app.R

/** PRT's TrueTime site, where riders sign up and request a developer key. */
const val TRUETIME_SIGN_UP_URL = "https://truetime.rideprt.org/bustime/home.jsp"

/** What the key screen can ask of [ApiKeyViewModel]; an interface so tests can record calls. */
interface ApiKeyActions {
    fun onInputChanged(input: String)

    fun submit()

    fun saveWithoutChecking()

    fun dismiss()
}

/**
 * Explains why OpenPRT needs a TrueTime key, links to PRT's sign-up page and takes the key. On
 * first launch the secondary button skips the setup; later it cancels. System back does the same.
 */
@Composable
fun ApiKeyScreen(state: ApiKeyUiState, actions: ApiKeyActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions::dismiss)
    val uriHandler = LocalUriHandler.current
    Scaffold(modifier = modifier) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(
                    if (state.firstRun) R.string.api_key_welcome_title else R.string.api_key_title
                ),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(stringResource(R.string.api_key_why))
            Text(stringResource(R.string.api_key_steps))
            TextButton(onClick = { uriHandler.openUri(TRUETIME_SIGN_UP_URL) }) {
                Text(stringResource(R.string.api_key_open_sign_up))
            }
            if (state.hasKey) Text(stringResource(R.string.api_key_replaces))
            OutlinedTextField(
                value = state.input,
                onValueChange = actions::onInputChanged,
                label = { Text(stringResource(R.string.api_key_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { actions.submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            CheckStatusText(state.check, onSaveAnyway = actions::saveWithoutChecking)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = actions::submit,
                    enabled = state.input.isNotBlank() && state.check != KeyCheckStatus.Checking
                ) {
                    Text(stringResource(R.string.api_key_save))
                }
                TextButton(onClick = actions::dismiss) {
                    Text(
                        stringResource(
                            if (state.firstRun) R.string.api_key_skip else R.string.api_key_cancel
                        )
                    )
                }
            }
            Text(
                text = stringResource(R.string.api_key_stored_locally),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckStatusText(status: KeyCheckStatus, onSaveAnyway: () -> Unit) {
    when (status) {
        KeyCheckStatus.Idle -> Unit

        KeyCheckStatus.Checking -> Text(stringResource(R.string.api_key_checking))

        is KeyCheckStatus.Rejected -> Text(
            text = if (status.messages.isEmpty()) {
                stringResource(R.string.api_key_rejected)
            } else {
                stringResource(R.string.api_key_rejected_with_reason, status.messages.first())
            },
            color = MaterialTheme.colorScheme.error
        )

        KeyCheckStatus.Unreachable -> Column {
            Text(
                text = stringResource(R.string.api_key_unreachable),
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onSaveAnyway) {
                Text(stringResource(R.string.api_key_save_anyway))
            }
        }
    }
}
