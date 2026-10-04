package org.openprt.app.trip

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.openprt.app.R

/**
 * Leave now / Depart at / Arrive by above the ways there. For the last two, a date button and a
 * time button open pickers; the date picker only offers the days the timetable covers, once
 * they are known. Choices go to [actions]; dates and times are read and shown in [zone].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTimeControls(
    time: TripTimeUiState,
    actions: TripPlanActions,
    zone: ZoneId,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TripTimeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == time.mode,
                    onClick = { actions.setTimeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, TripTimeMode.entries.size)
                ) {
                    Text(stringResource(mode.labelRes()))
                }
            }
        }
        val at = time.at
        if (time.mode != TripTimeMode.LEAVE_NOW && at != null) {
            val local = at.atZone(zone).toLocalDateTime()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(local, time.dates) { actions.setTime(it.atZone(zone).toInstant()) }
                TimeButton(local) { actions.setTime(it.atZone(zone).toInstant()) }
            }
        }
    }
}

private fun TripTimeMode.labelRes(): Int = when (this) {
    TripTimeMode.LEAVE_NOW -> R.string.trip_time_leave_now
    TripTimeMode.DEPART_AT -> R.string.trip_time_depart_at
    TripTimeMode.ARRIVE_BY -> R.string.trip_time_arrive_by
}

/** Shows [current]'s date; picking another keeps its time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateButton(
    current: LocalDateTime,
    dates: ClosedRange<LocalDate>?,
    onPick: (LocalDateTime) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.trip_time_change_date)
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(current.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
    }
    if (!open) return
    // The picker works in UTC midnights, whatever the zone.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.toLocalDate().utcMillis(),
        selectableDates = TimetableDates(dates)
    )
    DatePickerDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                open = false
                state.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.atTime(current.toLocalTime()))
                }
            }) { Text(stringResource(R.string.trip_time_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) {
                Text(stringResource(R.string.trip_time_cancel))
            }
        }
    ) {
        DatePicker(state)
    }
}

/** Shows [current]'s time of day; picking another keeps its date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeButton(current: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val description = stringResource(R.string.trip_time_change_time)
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(current.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
    }
    if (!open) return
    val state = rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    )
    AlertDialog(
        onDismissRequest = { open = false },
        confirmButton = {
            TextButton(onClick = {
                open = false
                onPick(current.toLocalDate().atTime(LocalTime.of(state.hour, state.minute)))
            }) { Text(stringResource(R.string.trip_time_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) {
                Text(stringResource(R.string.trip_time_cancel))
            }
        },
        text = { TimePicker(state) }
    )
}

/** Only the days in [dates] can be picked; any day while they are unknown. */
@OptIn(ExperimentalMaterial3Api::class)
private class TimetableDates(private val dates: ClosedRange<LocalDate>?) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = dates == null ||
        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() in dates

    override fun isSelectableYear(year: Int): Boolean =
        dates == null || year in dates.start.year..dates.endInclusive.year
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
