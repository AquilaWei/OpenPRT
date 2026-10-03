package org.openprt.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.openprt.app.R

/**
 * One block of related information in a bottom-sheet panel. Outlined so cards stay apart from
 * each other and from the sheet in both themes. With [onClick] the whole card can be tapped.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
    if (onClick == null) {
        Card(
            modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = inner
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = colors,
            border = border,
            content = inner
        )
    }
}

/** Minutes until a bus comes, large enough to read at a glance like a stop's countdown sign. */
@Composable
fun MinutesPill(minutes: Long, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(
            text = minutesText(minutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Minutes until a bus comes, or "Now" when it is under a minute away, like PRT's signs. */
@Composable
fun minutesText(minutes: Long): String = if (minutes <= 0) {
    stringResource(R.string.departures_now)
} else {
    stringResource(R.string.departures_minutes, minutes)
}

/** Where a time comes from, or that the bus is late. */
enum class TimeStatus { LIVE, SCHEDULED, DELAYED }

/**
 * A small label saying whether a time is [TimeStatus.LIVE] (green, with a dot like a live
 * broadcast), from the [TimeStatus.SCHEDULED] timetable, or [TimeStatus.DELAYED] (red).
 */
@Composable
fun StatusChip(status: TimeStatus, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content, text) = when (status) {
        TimeStatus.LIVE -> Triple(
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
            R.string.status_live
        )

        TimeStatus.SCHEDULED -> Triple(
            colors.surfaceVariant,
            colors.onSurfaceVariant,
            R.string.status_scheduled
        )

        TimeStatus.DELAYED -> Triple(
            colors.errorContainer,
            colors.onErrorContainer,
            R.string.departures_delayed
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            if (status == TimeStatus.LIVE) {
                Box(modifier = Modifier.size(6.dp).background(content, CircleShape))
            }
            Text(stringResource(text), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A line of text led by a small icon, e.g. a walking figure before the walking time. */
@Composable
fun IconText(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Decorative: the text says the same thing.
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Text(text = text, style = style, color = color)
    }
}
