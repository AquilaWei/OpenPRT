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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.openprt.app.R

/** Tall enough for about four results while leaving the map visible below. */
private val RESULTS_MAX_HEIGHT = 280.dp

/**
 * Search field for the trip's destination, floating over the map, with the matching places
 * below it. Once a destination is chosen the field itself shows it, with a button to clear it,
 * so the overlay stays one line tall and leaves the map visible; tapping it searches again.
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
    // Reset for each new destination, so picking one always ends the search.
    var editing by remember(state.destination) { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp
    ) {
        Column {
            val destination = state.destination
            if (destination != null && !editing && state.query.isEmpty()) {
                DestinationBar(destination, onEdit = { editing = true }, onClearDestination)
            } else {
                SearchField(
                    query = state.query,
                    onQueryChanged = onQueryChanged,
                    focusOnStart = editing,
                    onLeft = { editing = false }
                )
            }
            SearchStatusContent(
                status = state.search,
                onPlaceSelected = { place ->
                    // Hides the keyboard so the map is visible again.
                    focusManager.clearFocus()
                    onPlaceSelected(place)
                },
                onRetry = onRetry
            )
        }
    }
}

/**
 * The text field without its own outline, since the floating surface already frames it. While
 * it is focused and empty it mentions the other way to pick a destination.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    focusOnStart: Boolean,
    onLeft: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val transparent = Color.Transparent
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text(stringResource(R.string.destination_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        leadingIcon = { Icon(painterResource(R.drawable.ic_place), contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.destination_clear_query)
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = transparent,
            unfocusedContainerColor = transparent,
            focusedIndicatorColor = transparent,
            unfocusedIndicatorColor = transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (focused && !it.isFocused && query.isEmpty()) onLeft()
                focused = it.isFocused
            }
    )
    if (focused && query.isEmpty()) {
        SearchText(stringResource(R.string.destination_long_press_hint))
    }
    LaunchedEffect(focusOnStart) { if (focusOnStart) focusRequester.requestFocus() }
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

/** The chosen destination in place of the search field; tapping its text searches again. */
@Composable
private fun DestinationBar(destination: Destination, onEdit: () -> Unit, onClear: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp)
    ) {
        Icon(painterResource(R.drawable.ic_place), contentDescription = null)
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(
                    onClickLabel = stringResource(R.string.destination_change),
                    onClick = onEdit
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
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
