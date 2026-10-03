package org.openprt.app.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.openprt.app.R
import org.openprt.app.planner.RideLeg
import org.openprt.app.planner.WalkLeg
import org.openprt.app.ui.IconText
import org.openprt.app.ui.InfoCard
import org.openprt.app.ui.RouteBadge
import org.openprt.app.ui.displayHeadsign
import org.openprt.app.ui.displayName

/**
 * One chosen way to go, leg by leg: walks with their minutes and where they lead, and rides with
 * where to board and get off. Tapping a ride's live-bus button looks its bus up through
 * [actions]; when TrueTime has no data for it, the ride says its times are from the timetable.
 * The back button returns to the list of options. Times are shown in [zone].
 */
@Composable
fun TripDetailsPanel(
    selected: SelectedTrip,
    actions: TripPlanActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault()
) {
    val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
    val option = selected.option
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions::closeSelection) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.trip_back)
                    )
                }
                Column(modifier = Modifier.weight(1f)) { TripSummary(option, time) }
            }
        }
        InfoCard {
            // Bounded so a long trip scrolls inside the sheet instead of growing past the screen.
            Column(
                modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
            ) {
                val legs = option.plan.itinerary.legs.filter { it !is WalkLeg || it.minutes() > 0 }
                legs.forEachIndexed { index, leg ->
                    if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    when (leg) {
                        is WalkLeg -> WalkRow(leg)

                        is RideLeg -> RideRow(
                            ride = leg,
                            option = option,
                            lookup = selected.ride,
                            time = time,
                            onLiveBus = { actions.openRide(leg) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkRow(walk: WalkLeg) {
    val to = walk.to
    IconText(
        icon = R.drawable.ic_walk,
        text = if (to == null) {
            stringResource(R.string.trip_walk_to_destination, walk.minutes())
        } else {
            stringResource(R.string.trip_walk_to_stop, walk.minutes(), displayName(to.name))
        },
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun RideRow(
    ride: RideLeg,
    option: TripOption,
    lookup: RideLookup,
    time: DateTimeFormatter,
    onLiveBus: () -> Unit
) {
    val plan = option.plan
    // The first bus may follow a live prediction; later ones only have the timetable.
    val firstRide = ride == plan.itinerary.rides.first()
    val boarding = if (firstRide) option.boardingTime else plan.timeOf(ride.startSeconds)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // The live-bus button sits beside the route number, where it is seen without dragging
        // the sheet up.
        Row(verticalAlignment = Alignment.CenterVertically) {
            RouteBadge(ride.routeId, style = MaterialTheme.typography.titleLarge)
            Text(
                text = ride.headsign?.let {
                    stringResource(R.string.trip_toward, displayHeadsign(it))
                }.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            TextButton(onClick = onLiveBus) {
                IconText(
                    icon = R.drawable.ic_bus,
                    text = stringResource(R.string.trip_live_bus),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        StopAndTime(
            stringResource(R.string.trip_board_at, displayName(ride.from.name)),
            time.format(boarding)
        )
        StopAndTime(
            stringResource(R.string.trip_get_off_at, displayName(ride.to.name)),
            time.format(plan.timeOf(ride.endSeconds))
        )
        val note = when {
            lookup is RideLookup.Looking && lookup.ride == ride -> R.string.trip_finding_bus

            lookup is RideLookup.ScheduledOnly && lookup.ride == ride ->
                R.string.trip_scheduled_only

            else -> null
        }
        note?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A stop on the left and its time in a column on the right, so a long name never pushes the time
 * onto a line of its own.
 */
@Composable
private fun StopAndTime(stop: String, clock: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = stop,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Text(text = clock, style = MaterialTheme.typography.titleSmall)
    }
}
