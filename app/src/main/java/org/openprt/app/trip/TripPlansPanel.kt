package org.openprt.app.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.openprt.app.R
import org.openprt.app.departures.PanelText
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.ui.RouteBadge

/**
 * The ways to the chosen destination, shown in the home screen's bottom sheet in place of the
 * nearby departures. Times are shown in [zone]; [onRetry] plans again when the timetable was
 * missing.
 */
@Composable
fun TripPlansPanel(
    state: TripPlanUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.trip_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (state) {
            TripPlanUiState.Planning -> PanelText(stringResource(R.string.trip_planning))

            is TripPlanUiState.Results -> OptionList(state.options, zone)

            is TripPlanUiState.NoRoute -> PanelText(stringResource(state.reason.textRes()))

            TripPlanUiState.NoTimetable -> {
                PanelText(stringResource(R.string.trip_no_timetable))
                TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.trip_retry))
                }
            }
        }
    }
}

private fun NoRouteReason.textRes(): Int = when (this) {
    NoRouteReason.NO_STOP_NEAR_ORIGIN -> R.string.trip_no_stop_near_origin
    NoRouteReason.NO_STOP_NEAR_DESTINATION -> R.string.trip_no_stop_near_destination
    NoRouteReason.NO_CONNECTION -> R.string.trip_no_connection
}

@Composable
private fun OptionList(options: List<TripOption>, zone: ZoneId) {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    // Bounded so the list scrolls inside the sheet instead of growing past the screen.
    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
        items(options) { option ->
            OptionRow(option, time)
            HorizontalDivider()
        }
    }
}

@Composable
private fun OptionRow(option: TripOption, time: DateTimeFormatter) {
    ListItem(
        leadingContent = {
            Text(
                text = stringResource(R.string.trip_total_minutes, option.totalMinutes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp)
            )
        },
        headlineContent = {
            Text(
                stringResource(
                    R.string.trip_times,
                    time.format(option.departureTime),
                    time.format(option.arrivalTime)
                )
            )
        },
        supportingContent = {
            Column {
                Legs(option.legs)
                Text(transfersText(option.transfers))
                Text(
                    text = stringResource(
                        if (option.live) R.string.trip_first_bus_live else R.string.trip_first_bus,
                        option.firstRoute,
                        option.boardingStopName,
                        time.format(option.boardingTime)
                    ),
                    // Green for live data, the same in both themes.
                    color = if (option.live) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    )
}

/** Each leg is its own text so a long trip wraps between legs, not inside a route number. */
@Composable
private fun Legs(legs: List<LegSummary>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) Text("›")
            when (leg) {
                is LegSummary.Walk -> Text(stringResource(R.string.trip_walk_minutes, leg.minutes))

                is LegSummary.Ride ->
                    RouteBadge(leg.route, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun transfersText(transfers: Int): String = if (transfers == 0) {
    stringResource(R.string.trip_direct)
} else {
    pluralStringResource(R.plurals.trip_transfers, transfers, transfers)
}
