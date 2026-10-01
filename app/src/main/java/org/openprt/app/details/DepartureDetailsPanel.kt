package org.openprt.app.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openprt.app.R
import org.openprt.app.departures.PanelText

/**
 * Bottom-sheet content for one selected departure: which bus and where to board it, then the
 * stops of its route with the boarding stop marked. [onBack] returns to the nearby list.
 */
@Composable
fun DepartureDetailsPanel(
    state: DepartureDetailsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val departure = state.departure
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.details_back)
                )
            }
            Text(
                text = departure.route,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.departures_destination, departure.destination),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        PanelText(
            stringResource(R.string.details_board_at, departure.stopName, departure.walkMinutes)
        )
        when (val route = state.route) {
            RouteStatus.Loading -> PanelText(stringResource(R.string.details_route_loading))

            RouteStatus.NotFound -> PanelText(stringResource(R.string.details_route_not_found))

            is RouteStatus.Failed -> PanelText(
                stringResource(R.string.details_route_failed),
                color = MaterialTheme.colorScheme.error
            )

            is RouteStatus.Ready -> RouteStopList(route.shape)
        }
    }
}

@Composable
private fun RouteStopList(shape: RouteShape) {
    // Opens scrolled to the boarding stop; the stops before it rarely matter.
    val listState = remember(shape) {
        LazyListState(
            firstVisibleItemIndex = shape.stops.indexOf(shape.boardingStop).coerceAtLeast(0)
        )
    }
    LazyColumn(state = listState, modifier = Modifier.heightIn(max = 400.dp)) {
        items(shape.stops) { stop ->
            val boarding = stop == shape.boardingStop
            ListItem(
                headlineContent = {
                    Text(
                        text = stop.name,
                        fontWeight = if (boarding) FontWeight.Bold else FontWeight.Normal
                    )
                },
                supportingContent = if (boarding) {
                    { Text(stringResource(R.string.details_board_here)) }
                } else {
                    null
                }
            )
        }
    }
}
