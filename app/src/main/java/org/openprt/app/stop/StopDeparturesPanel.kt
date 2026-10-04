package org.openprt.app.stop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openprt.app.R
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.PanelText
import org.openprt.app.ui.InfoCard
import org.openprt.app.ui.MinutesPill
import org.openprt.app.ui.RouteBadge
import org.openprt.app.ui.StatusChip
import org.openprt.app.ui.TimeStatus
import org.openprt.app.ui.displayHeadsign
import org.openprt.app.ui.displayName
import org.openprt.app.ui.trueTimeErrorReason

/**
 * Bottom-sheet content for a stop tapped on the map: its name and stop number, then the buses
 * leaving it, live when TrueTime has them and from the timetable otherwise. Tapping a live bus
 * reports it through [onDepartureClick]; timetabled rows have no bus to follow and do nothing.
 * [onBack] closes the stop.
 */
@Composable
fun StopDeparturesPanel(
    state: StopDeparturesUiState,
    onBack: () -> Unit,
    onDepartureClick: (DepartureItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.stop_back)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName(state.stop.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.stop_number, state.stop.stopId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val source = state.source
        if (source is StopTimesSource.Scheduled) ScheduledNote(source.liveError)
        when {
            state.departures.isNotEmpty() -> StopDepartureList(state.departures, onDepartureClick)
            source == StopTimesSource.Loading -> PanelText(stringResource(R.string.stop_loading))
            else -> PanelText(stringResource(R.string.stop_empty))
        }
    }
}

/** Says why the times are from the timetable, so a missing key or a dropped link is visible. */
@Composable
private fun ScheduledNote(liveError: TrueTimeError?) {
    PanelText(
        if (liveError == null) {
            stringResource(R.string.stop_scheduled_no_live)
        } else {
            stringResource(R.string.stop_scheduled_failed, trueTimeErrorReason(liveError))
        }
    )
}

@Composable
private fun StopDepartureList(
    departures: List<StopDeparture>,
    onDepartureClick: (DepartureItem) -> Unit
) {
    // Bounded so the list scrolls inside the sheet instead of growing past the screen.
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(departures) { row -> StopDepartureRow(row, onDepartureClick) }
    }
}

@Composable
private fun StopDepartureRow(row: StopDeparture, onDepartureClick: (DepartureItem) -> Unit) {
    val departure = row.departure
    InfoCard(onClick = departure?.let { { onDepartureClick(it) } }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            RouteBadge(row.route)
            Text(
                text = stringResource(
                    R.string.departures_destination,
                    displayHeadsign(row.destination)
                ),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MinutesPill(row.minutes)
                StatusChip(
                    when {
                        departure == null -> TimeStatus.SCHEDULED
                        row.delayed -> TimeStatus.DELAYED
                        else -> TimeStatus.LIVE
                    }
                )
            }
        }
    }
}
