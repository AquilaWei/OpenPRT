package org.openprt.app.departures

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.openprt.app.R
import org.openprt.app.data.truetime.TrueTimeError

/**
 * The list of departures near the user, shown in the home screen's bottom sheet. A failed
 * refresh keeps the previous list and says how old it is; times are shown in [zone].
 */
@Composable
fun DeparturesPanel(
    state: DeparturesUiState,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.departures_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        val status = state.status
        if (status is DeparturesStatus.Failed) {
            FailureText(status.error, state.lastUpdated, zone)
        }
        when {
            state.departures.isNotEmpty() -> DepartureList(state.departures)

            status == DeparturesStatus.Loading -> PanelText(
                stringResource(R.string.departures_loading)
            )

            // A failure before any success has nothing more to say than FailureText.
            status == DeparturesStatus.Ready -> PanelText(stringResource(R.string.departures_empty))

            else -> Unit
        }
    }
}

@Composable
private fun DepartureList(departures: List<DepartureItem>) {
    // Bounded so the list scrolls inside the sheet instead of growing past the screen.
    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
        items(departures) { departure ->
            DepartureRow(departure)
            HorizontalDivider()
        }
    }
}

@Composable
private fun DepartureRow(departure: DepartureItem) {
    ListItem(
        leadingContent = {
            Text(
                text = departure.route,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp)
            )
        },
        headlineContent = {
            Text(stringResource(R.string.departures_destination, departure.destination))
        },
        supportingContent = {
            Text(
                stringResource(
                    R.string.departures_stop_and_walk,
                    departure.direction,
                    departure.stopName,
                    departure.walkMinutes
                )
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(
                        R.string.departures_minutes,
                        departure.minutesUntilDeparture
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                if (departure.delayed) {
                    Text(
                        text = stringResource(R.string.departures_delayed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    )
}

@Composable
private fun FailureText(error: TrueTimeError, lastUpdated: Instant?, zone: ZoneId) {
    val reason = stringResource(
        if (error == TrueTimeError.MissingApiKey) {
            R.string.departures_failed_missing_key
        } else {
            R.string.departures_failed
        }
    )
    val text = if (lastUpdated == null) {
        reason
    } else {
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
        stringResource(R.string.departures_last_updated, reason, time.format(lastUpdated))
    }
    PanelText(text, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun PanelText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
