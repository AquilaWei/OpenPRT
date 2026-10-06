package org.openprt.app.departures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
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
import org.openprt.app.data.truetime.ApiProblem
import org.openprt.app.data.truetime.TrueTimeError
import org.openprt.app.data.truetime.problem
import org.openprt.app.ui.IconText
import org.openprt.app.ui.InfoCard
import org.openprt.app.ui.MinutesPill
import org.openprt.app.ui.RouteBadge
import org.openprt.app.ui.StatusChip
import org.openprt.app.ui.TimeStatus
import org.openprt.app.ui.displayHeadsign
import org.openprt.app.ui.displayName

/**
 * The departures near the user, shown in the home screen's bottom sheet as one card per route
 * with a row for each direction. A failed refresh keeps the previous list and says why (offline,
 * key refused, daily limit used up) and how old the list is; times are shown in [zone]. Tapping a row reports it through [onDepartureClick].
 */
@Composable
fun DeparturesPanel(
    state: DeparturesUiState,
    onDepartureClick: (DepartureItem) -> Unit,
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
            state.departures.isNotEmpty() -> DepartureList(state.departures, onDepartureClick)

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
private fun DepartureList(
    departures: List<DepartureItem>,
    onDepartureClick: (DepartureItem) -> Unit
) {
    // Bounded so the list scrolls inside the sheet instead of growing past the screen.
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(groupByRoute(departures), key = { it.route }) { group ->
            RouteCard(group, onDepartureClick)
        }
    }
}

/** One route: its badge on the left, then a row for each direction it can be caught in. */
@Composable
private fun RouteCard(group: DepartureGroup, onDepartureClick: (DepartureItem) -> Unit) {
    InfoCard {
        Row {
            // A fixed-width column keeps the rows of every card lined up.
            Box(modifier = Modifier.width(72.dp).padding(top = 8.dp, end = 12.dp)) {
                RouteBadge(group.route, style = MaterialTheme.typography.titleLarge)
            }
            Column(modifier = Modifier.weight(1f)) {
                group.rows.forEachIndexed { index, departure ->
                    if (index > 0) HorizontalDivider()
                    DirectionRow(departure, onClick = { onDepartureClick(departure) })
                }
            }
        }
    }
}

@Composable
private fun DirectionRow(departure: DepartureItem, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconText(
                icon = R.drawable.ic_arrow_forward,
                text = directionLabel(departure.direction),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(
                    R.string.departures_destination,
                    displayHeadsign(departure.destination)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconText(icon = R.drawable.ic_place, text = displayName(departure.stopName))
            IconText(
                icon = R.drawable.ic_walk,
                text = stringResource(R.string.departures_walk, departure.walkMinutes)
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            MinutesPill(departure.minutesUntilDeparture)
            // Every nearby departure is a TrueTime prediction, so it is live unless late.
            StatusChip(if (departure.delayed) TimeStatus.DELAYED else TimeStatus.LIVE)
        }
    }
}

@Composable
private fun FailureText(error: TrueTimeError, lastUpdated: Instant?, zone: ZoneId) {
    val reason = stringResource(
        when {
            error == TrueTimeError.MissingApiKey -> R.string.departures_failed_missing_key

            error is TrueTimeError.Network -> R.string.departures_offline

            error is TrueTimeError.Api && error.problem == ApiProblem.INVALID_KEY ->
                R.string.departures_invalid_key

            error is TrueTimeError.Api && error.problem == ApiProblem.QUOTA_EXCEEDED ->
                R.string.departures_quota_exceeded

            else -> R.string.departures_failed
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

/** A line of explanatory text in a bottom-sheet panel. */
@Composable
internal fun PanelText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        color = color,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
