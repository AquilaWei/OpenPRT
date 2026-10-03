package org.openprt.app.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.openprt.app.R
import org.openprt.app.departures.DepartureItem
import org.openprt.app.departures.directionLabel
import org.openprt.app.departures.oppositeDirectionName
import org.openprt.app.map.StopMarker
import org.openprt.app.ui.IconText
import org.openprt.app.ui.InfoCard
import org.openprt.app.ui.RouteBadge
import org.openprt.app.ui.StatusChip
import org.openprt.app.ui.TimeStatus
import org.openprt.app.ui.displayHeadsign
import org.openprt.app.ui.displayName
import org.openprt.app.ui.minutesText
import org.openprt.app.ui.theme.LocalOpenPrtColors
import org.openprt.app.ui.trueTimeErrorReason

/**
 * Bottom-sheet content for one selected departure, as three cards: the route with a switch
 * between its two directions, when and where the bus arrives, and a timeline of its stops with
 * the boarding stop and the bus marked. Update times are shown in [zone].
 *
 * [otherDirection] is the nearby departure on the same route going the other way; choosing that
 * direction reports it through [onSwitchDirection]. When it is null the other direction is shown
 * disabled. [onBack] returns to the nearby list.
 */
@Composable
fun DepartureDetailsPanel(
    state: DepartureDetailsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    otherDirection: DepartureItem? = null,
    onSwitchDirection: (DepartureItem) -> Unit = {},
    zone: ZoneId = ZoneId.systemDefault()
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeaderCard(state.departure, otherDirection, onSwitchDirection, onBack)
        ArrivalCard(state.departure, state.bus, zone)
        when (val route = state.route) {
            RouteStatus.Loading -> PanelNote(stringResource(R.string.details_route_loading))

            RouteStatus.NotFound -> PanelNote(stringResource(R.string.details_route_not_found))

            is RouteStatus.Failed -> PanelNote(
                stringResource(R.string.details_route_failed, trueTimeErrorReason(route.error)),
                error = true
            )

            is RouteStatus.Ready -> StopTimeline(route.shape, state.bus.progress)
        }
    }
}

@Composable
private fun HeaderCard(
    departure: DepartureItem,
    otherDirection: DepartureItem?,
    onSwitchDirection: (DepartureItem) -> Unit,
    onBack: () -> Unit
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.details_back)
                )
            }
            RouteBadge(departure.route, style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(
                    R.string.departures_destination,
                    displayHeadsign(departure.destination)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        DirectionSwitch(departure, otherDirection, onSwitchDirection)
    }
}

/**
 * The route's two directions side by side, the current one selected. The pair is shown in a
 * fixed order so the buttons do not swap places when switching. A direction whose opposite is
 * unknown (e.g. a loop) is shown on its own.
 */
@Composable
private fun DirectionSwitch(
    departure: DepartureItem,
    otherDirection: DepartureItem?,
    onSwitchDirection: (DepartureItem) -> Unit
) {
    val otherName = otherDirection?.direction ?: oppositeDirectionName(departure.direction)
    if (otherName == null) {
        IconText(
            icon = R.drawable.ic_arrow_forward,
            text = directionLabel(departure.direction),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }
    val names = listOf(departure.direction, otherName).sorted()
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        names.forEachIndexed { index, name ->
            val current = name == departure.direction
            SegmentedButton(
                selected = current,
                onClick = {
                    if (!current &&
                        otherDirection != null
                    ) {
                        onSwitchDirection(otherDirection)
                    }
                },
                enabled = current || otherDirection != null,
                shape = SegmentedButtonDefaults.itemShape(index, names.size),
                label = { Text(directionLabel(name)) }
            )
        }
    }
    if (otherDirection == null) {
        Text(
            text = stringResource(R.string.details_no_other_direction, directionLabel(otherName)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun ArrivalCard(departure: DepartureItem, bus: LiveBus, zone: ZoneId) {
    InfoCard {
        when (val arrival = bus.arrival) {
            Arrival.Loading -> Text(stringResource(R.string.details_bus_locating))

            is Arrival.Expected -> ExpectedArrival(arrival, bus.progress)

            Arrival.Departed -> Text(
                text = stringResource(R.string.details_bus_departed),
                style = MaterialTheme.typography.titleMedium
            )
        }
        IconText(
            icon = R.drawable.ic_place,
            text = stringResource(R.string.details_board_at, displayName(departure.stopName)),
            modifier = Modifier.padding(top = 8.dp)
        )
        IconText(
            icon = R.drawable.ic_walk,
            text = stringResource(R.string.departures_walk, departure.walkMinutes)
        )
        UpdateText(bus, zone)
    }
}

@Composable
private fun ExpectedArrival(arrival: Arrival.Expected, progress: BusProgress?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.details_arrives_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = minutesText(arrival.minutes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatusChip(if (arrival.delayed) TimeStatus.DELAYED else TimeStatus.LIVE)
            when (val away = progress?.stopsAway) {
                null -> Unit

                0 -> Text(
                    text = stringResource(R.string.details_at_your_stop),
                    style = MaterialTheme.typography.labelLarge
                )

                else -> Text(
                    text = pluralStringResource(R.plurals.details_stops_away, away, away),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun UpdateText(bus: LiveBus, zone: ZoneId) {
    val time = bus.lastUpdated?.let { formatTime(it, zone) }
    val error = bus.error
    val (text, failed) = when {
        error != null && time != null -> stringResource(
            R.string.details_bus_failed_since,
            trueTimeErrorReason(error),
            time
        ) to true

        error != null ->
            stringResource(R.string.details_bus_failed, trueTimeErrorReason(error)) to true

        time != null -> stringResource(R.string.details_updated, time) to false

        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(top = 4.dp)
    )
}

// With seconds, since the bus is refreshed more often than once a minute.
private fun formatTime(time: Instant, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(zone).format(time)

/** A short status line about the route, in place of the timeline. */
@Composable
private fun PanelNote(text: String, error: Boolean = false) {
    Text(
        text = text,
        color = if (error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

/** One row of the stop timeline: a stop, or the bus between two stops. */
private sealed interface TimelineRow {
    data class Stop(val stop: StopMarker, val boarding: Boolean, val passed: Boolean) :
        TimelineRow

    data object Bus : TimelineRow
}

private fun timelineRows(shape: RouteShape, progress: BusProgress?): List<TimelineRow> {
    val passed = progress?.passedStops ?: 0
    val stops = shape.stops.mapIndexed { index, stop ->
        TimelineRow.Stop(stop, boarding = stop == shape.boardingStop, passed = index < passed)
    }
    if (progress == null) return stops
    return stops.take(passed) + TimelineRow.Bus + stops.drop(passed)
}

/** Rows shown above the boarding stop when the timeline opens. */
private const val ROWS_ABOVE_BOARDING = 2

/** How far before the boarding stop an approaching bus still opens the timeline at the bus. */
private const val BUS_IN_VIEW_ROWS = 6

/**
 * The row the timeline opens at: one row above the approaching bus when it is a few stops away,
 * so the stop it last passed, the bus and the boarding stop are all in view; otherwise just
 * above the boarding stop.
 */
internal fun firstTimelineRowIndex(boardingIndex: Int, busIndex: Int?): Int {
    val nearBus = busIndex?.takeIf { it < boardingIndex && boardingIndex - it <= BUS_IN_VIEW_ROWS }
    val first = if (nearBus != null) nearBus - 1 else boardingIndex - ROWS_ABOVE_BOARDING
    return first.coerceAtLeast(0)
}

private fun firstTimelineRow(rows: List<TimelineRow>): Int = firstTimelineRowIndex(
    boardingIndex = rows.indexOfFirst { it is TimelineRow.Stop && it.boarding },
    busIndex = rows.indexOf(TimelineRow.Bus).takeIf { it >= 0 }
)

@Composable
private fun StopTimeline(shape: RouteShape, progress: BusProgress?) {
    val rows = timelineRows(shape, progress)
    // Keyed on the shape and on whether the bus is placed yet (it usually arrives one refresh
    // after the route), so the list does not jump each time the bus moves.
    val listState = remember(shape, progress == null) {
        LazyListState(firstVisibleItemIndex = firstTimelineRow(rows))
    }
    InfoCard {
        Text(
            text = stringResource(R.string.details_stops_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = 300.dp)) {
            itemsIndexed(rows) { index, row ->
                val first = index == 0
                val last = index == rows.lastIndex
                when (row) {
                    is TimelineRow.Stop -> StopRow(row, first, last)
                    TimelineRow.Bus -> BusRow(first, last)
                }
            }
        }
    }
}

/** The left-hand rail of the timeline: a vertical line, broken at the ends, and a [marker]. */
@Composable
private fun TimelineRail(first: Boolean, last: Boolean, marker: @Composable () -> Unit) {
    val lineColor = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2
                val top = if (first) size.height / 2 else 0f
                val bottom = if (last) size.height / 2 else size.height
                drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 3.dp.toPx())
            }
    ) {
        marker()
    }
}

@Composable
private fun StopRow(row: TimelineRow.Stop, first: Boolean, last: Boolean) {
    val passedText = stringResource(R.string.details_passed)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            // Stops the bus has already passed matter less, so they fade back.
            .alpha(if (row.passed) 0.45f else 1f)
            .semantics(mergeDescendants = true) {
                if (row.passed) stateDescription = passedText
            }
    ) {
        TimelineRail(first, last) {
            if (row.boarding) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(LocalOpenPrtColors.current.accent, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                text = displayName(row.stop.name),
                fontWeight = if (row.boarding) FontWeight.Bold else FontWeight.Normal
            )
        }
        if (row.boarding) BoardHereChip()
    }
}

@Composable
private fun BoardHereChip() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = stringResource(R.string.details_board_here),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BusRow(first: Boolean, last: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
    ) {
        TimelineRail(first, last) {
            // The same green bus as on the map.
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(
                    painter = painterResource(R.drawable.ic_bus),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(3.dp).size(18.dp)
                )
            }
        }
        Text(
            text = stringResource(R.string.details_bus_here),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}
