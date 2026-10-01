package org.openprt.app.destination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.openprt.app.R

/** Tall enough for about four results while leaving the map visible below. */
private val RESULTS_MAX_HEIGHT = 280.dp

/**
 * Search field for the trip's destination, floating over the map, with the matching places
 * below it and, once one is chosen, the destination with a button to clear it.
 */
@Composable
fun DestinationSearch(
    state: DestinationUiState,
    onQueryChanged: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onRetry: () -> Unit,
    onClearDestination: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(stringResource(R.string.destination_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChanged("") }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(
                                    R.string.destination_clear_query
                                )
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            )
            SearchStatusContent(
                status = state.search,
                onPlaceSelected = { place ->
                    // Hides the keyboard so the map is visible again.
                    focusManager.clearFocus()
                    onPlaceSelected(place)
                },
                onRetry = onRetry
            )
            state.destination?.let { DestinationRow(it, onClearDestination) }
        }
    }
}

@Composable
private fun SearchStatusContent(
    status: SearchStatus,
    onPlaceSelected: (Place) -> Unit,
    onRetry: () -> Unit
) {
    when (status) {
        SearchStatus.Idle -> Unit

        SearchStatus.Searching -> SearchText(stringResource(R.string.destination_searching))

        is SearchStatus.Results -> if (status.places.isEmpty()) {
            SearchText(stringResource(R.string.destination_no_results))
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = RESULTS_MAX_HEIGHT)) {
                items(status.places) { place ->
                    ListItem(
                        headlineContent = { Text(place.name) },
                        supportingContent = place.description.takeIf { it.isNotEmpty() }?.let {
                            { Text(it) }
                        },
                        modifier = Modifier.clickable { onPlaceSelected(place) }
                    )
                    HorizontalDivider()
                }
            }
        }

        is SearchStatus.Failed -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.destination_search_failed),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.destination_retry)) }
        }
    }
}

@Composable
private fun DestinationRow(destination: Destination, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, bottom = 4.dp)
    ) {
        Text(
            text = stringResource(
                R.string.destination_to,
                destination.name ?: stringResource(
                    R.string.destination_pinned,
                    // Five decimals is about a meter, plenty to recognize the spot.
                    String.format(Locale.US, "%.5f", destination.location.latitude),
                    String.format(Locale.US, "%.5f", destination.location.longitude)
                )
            ),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.destination_clear)
            )
        }
    }
}

@Composable
private fun SearchText(text: String) {
    Text(text = text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
}
