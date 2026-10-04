package org.openprt.app.trip

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.openprt.app.R
import org.openprt.app.departures.PanelText
import org.openprt.app.planner.NoRouteReason
import org.openprt.app.ui.IconText
import org.openprt.app.ui.InfoCard
import org.openprt.app.ui.RouteBadge
import org.openprt.app.ui.StatusChip
import org.openprt.app.ui.TimeStatus
import org.openprt.app.ui.displayName

/**
 * The ways to the chosen destination, shown in the home screen's bottom sheet in place of the
 * nearby departures. Tapping an option shows it leg by leg ([TripDetailsPanel]); the other
 * requests, such as planning again when the timetable was missing or another [time], go to
 * [actions]. Times are shown, and picked, in [zone]; those on another day than [today] carry
 * their date.
 */
@Composable
fun TripPlansPanel(
    state: TripPlanUiState,
    actions: TripPlanActions,
    modifier: Modifier = Modifier,
    time: TripTimeUiState = TripTimeUiState(),
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone)
) {
    val selected = (state as? TripPlanUiState.Results)?.selected
    if (selected != null) {
        TripDetailsPanel(selected, actions, modifier, zone, today)
        return
    }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.trip_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        TripTimeControls(time, actions, zone, modifier = Modifier.padding(bottom = 8.dp))
        when (state) {
            TripPlanUiState.Planning -> PanelText(stringResource(R.string.trip_planning))

            is TripPlanUiState.Results ->
                OptionList(state.options, actions::select, TripClockFormat(zone, today))

            is TripPlanUiState.NoRoute -> PanelText(stringResource(state.reason.textRes()))

            TripPlanUiState.NoTimetable -> {
                PanelText(stringResource(R.string.trip_no_timetable))
                TextButton(
                    onClick = actions::retry,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
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
private fun OptionList(
    options: List<TripOption>,
    onSelect: (TripOption) -> Unit,
    time: TripClockFormat
) {
    // Bounded so the list scrolls inside the sheet instead of growing past the screen.
    LazyColumn(
        modifier = Modifier.heightIn(max = 400.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option -> OptionCard(option, time, onClick = { onSelect(option) }) }
    }
}

/**
 * One way to go: total time and clock times on top, the legs, then the first bus. For "Arrive
 * by" it starts with when to leave. It warns when a late first bus or a long walk may miss the
 * deadline or a bus ([TripWarning]). The chevron says the card opens; riders did not find out by
 * themselves that it could be tapped.
 */
@Composable
private fun OptionCard(option: TripOption, time: TripClockFormat, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (option.deadline != null) {
                    Text(
                        text = stringResource(
                            R.string.trip_leave_by,
                            time.format(option.departureTime)
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TripSummary(option, time)
                Legs(option.legs)
                Text(
                    text = stringResource(
                        R.string.trip_first_bus,
                        option.firstRoute,
                        displayName(option.boardingStopName),
                        time.format(option.boardingTime)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    // Green for live data, the same in both themes.
                    color = if (option.live) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                TripWarning(option, time)
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.trip_open_details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/**
 * Why [option] may not work out, if it may not: a walk along the streets too long to catch a bus,
 * or an arrival after the "Arrive by" deadline, blamed on the walks when they are longer than
 * planned and otherwise on the late first bus. Nothing when the option is fine.
 */
@Composable
internal fun TripWarning(option: TripOption, time: TripClockFormat) {
    val text = when {
        option.missesBus -> stringResource(R.string.trip_walk_may_miss_bus)

        !option.late || option.deadline == null -> return

        option.walksLonger ->
            stringResource(R.string.trip_walk_may_be_late, time.format(option.deadline))

        else -> stringResource(R.string.trip_may_be_late, time.format(option.deadline))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

/** Total minutes, clock times, transfers and whether the first bus is live, on one row. */
@Composable
internal fun TripSummary(option: TripOption, time: TripClockFormat) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.trip_total_minutes, option.totalMinutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.trip_times,
                    time.format(option.departureTime),
                    time.format(option.arrivalTime)
                ),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = transfersText(option.transfers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusChip(if (option.live) TimeStatus.LIVE else TimeStatus.SCHEDULED)
    }
}

/** Each leg is its own text so a long trip wraps between legs, not inside a route number. */
@Composable
private fun Legs(legs: List<LegSummary>) {
    FlowRow(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        legs.forEachIndexed { index, leg ->
            if (index > 0) Text("›")
            when (leg) {
                is LegSummary.Walk ->
                    IconText(
                        R.drawable.ic_walk,
                        stringResource(R.string.trip_walk_minutes, leg.minutes)
                    )

                is LegSummary.Ride ->
                    RouteBadge(leg.route, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Trip times as clock times, with the date in front of those not on [today]: a plan for the next
 * morning would otherwise read as this morning's.
 */
internal class TripClockFormat(private val zone: ZoneId, private val today: LocalDate) {
    private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    private val date = DateTimeFormatter
        .ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEMMMd"))
        .withZone(zone)

    fun format(at: Instant): String = if (at.atZone(zone).toLocalDate() == today) {
        clock.format(at)
    } else {
        "${date.format(at)} ${clock.format(at)}"
    }
}

@Composable
private fun transfersText(transfers: Int): String = if (transfers == 0) {
    stringResource(R.string.trip_direct)
} else {
    pluralStringResource(R.plurals.trip_transfers, transfers, transfers)
}
