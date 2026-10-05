package org.openprt.app.map

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openprt.app.R

/** How a legend entry is drawn, matching the shape of its map layer. */
enum class LegendSymbol {
    /** A small ringed circle, like the stops along a route. */
    DOT,

    /** A large ringed circle, like the user and the destination. */
    LARGE_DOT,

    /** A nearby stop's sign: a ringed rounded square with a bus glyph. */
    STOP_SIGN,

    /** The bigger sign of the boarding or tapped stop. */
    LARGE_STOP_SIGN,

    /** The bus badge: a ringed disc with a bus glyph. */
    BUS,
    LINE,
    DASHED_LINE
}

/**
 * One kind of map marker and what it means. Colors are the `#RRGGBB` strings of the
 * [MapPalette] the map layers are drawn with: [color] fills the symbol, [outline] rings it and
 * [glyph] colors the bus drawn on the bus badge and the stop signs.
 */
data class LegendEntry(
    val symbol: LegendSymbol,
    @param:StringRes val label: Int,
    val color: String,
    val outline: String? = null,
    val glyph: String? = null
)

/**
 * Every kind of marker the map draws with [palette], in the order a rider meets them. Taken
 * from the same palette as the map layers, so the legend changes with the theme as the map does.
 */
fun mapLegend(palette: MapPalette): List<LegendEntry> = listOf(
    LegendEntry(LegendSymbol.LARGE_DOT, R.string.legend_user, palette.user, palette.markerOutline),
    LegendEntry(
        LegendSymbol.STOP_SIGN,
        R.string.legend_stop,
        palette.stop,
        palette.stopOutline,
        palette.stopGlyph
    ),
    LegendEntry(
        LegendSymbol.LARGE_STOP_SIGN,
        R.string.legend_boarding_stop,
        palette.boardingStop,
        palette.markerOutline,
        palette.boardingStopGlyph
    ),
    LegendEntry(
        LegendSymbol.DOT,
        R.string.legend_route_stop,
        palette.routeStop,
        palette.routeStopOutline
    ),
    LegendEntry(LegendSymbol.LINE, R.string.legend_route, palette.routeLine),
    LegendEntry(LegendSymbol.DASHED_LINE, R.string.legend_walk, palette.user),
    LegendEntry(
        LegendSymbol.BUS,
        R.string.legend_bus,
        palette.bus,
        palette.markerOutline,
        palette.busGlyph
    ),
    LegendEntry(
        LegendSymbol.LARGE_DOT,
        R.string.legend_destination,
        palette.destination,
        palette.markerOutline
    )
)

/** A dialog listing what each map marker drawn with [palette] means. */
@Composable
fun MapLegendDialog(palette: MapPalette, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.legend_close)) }
        },
        title = { Text(stringResource(R.string.legend_title)) },
        text = {
            // Scrolls on short screens and with large fonts, where eight rows do not fit.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                mapLegend(palette).forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.size(SWATCH_SIZE), Alignment.Center) {
                            LegendSwatch(entry)
                        }
                        Text(stringResource(entry.label))
                    }
                }
            }
        }
    )
}

private val SWATCH_SIZE = 32.dp

@Composable
private fun LegendSwatch(entry: LegendEntry) {
    val color = entry.color.toColor()
    val outline = entry.outline?.toColor() ?: Color.Transparent
    when (entry.symbol) {
        LegendSymbol.DOT -> RingedDot(12.dp, 2.dp, color, outline)

        LegendSymbol.LARGE_DOT -> RingedDot(20.dp, 3.dp, color, outline)

        LegendSymbol.BUS -> GlyphBadge(28.dp, 2.dp, CircleShape, color, outline, entry.glyph)

        // The map's sign sizes (STOP_SIGN_DP, LARGE_STOP_SIGN_DP) and proportions (StopSign.kt).
        LegendSymbol.STOP_SIGN ->
            GlyphBadge(22.dp, 2.dp, RoundedCornerShape(25), color, outline, entry.glyph)

        LegendSymbol.LARGE_STOP_SIGN ->
            GlyphBadge(30.dp, 3.dp, RoundedCornerShape(25), color, outline, entry.glyph)

        LegendSymbol.LINE, LegendSymbol.DASHED_LINE -> Canvas(Modifier.size(SWATCH_SIZE)) {
            val width = (if (entry.symbol == LegendSymbol.LINE) 5.dp else 4.dp).toPx()
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = width,
                cap = StrokeCap.Round,
                // The map's dash pattern: 1 line width on, 1.5 off, with round caps.
                pathEffect = if (entry.symbol == LegendSymbol.DASHED_LINE) {
                    PathEffect.dashPathEffect(floatArrayOf(width, width * 1.5f))
                } else {
                    null
                }
            )
        }
    }
}

/** A [shape] of [fill] ringed in [outline] with the bus glyph in [glyph], as on the map. */
@Composable
private fun GlyphBadge(
    size: Dp,
    ring: Dp,
    shape: Shape,
    fill: Color,
    outline: Color,
    glyph: String?
) {
    Box(
        modifier = Modifier
            .size(size)
            .border(ring, outline, shape)
            .background(fill, shape),
        contentAlignment = Alignment.Center
    ) {
        // Decorative: the label next to it says what it is.
        Icon(
            painter = painterResource(R.drawable.ic_bus),
            contentDescription = null,
            tint = glyph?.toColor() ?: Color.White,
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

@Composable
private fun RingedDot(diameter: Dp, ring: Dp, fill: Color, outline: Color) {
    Canvas(Modifier.size(diameter)) {
        val radius = size.minDimension / 2
        drawCircle(outline, radius)
        drawCircle(fill, radius - ring.toPx())
    }
}

/** Parses a palette's `#RRGGBB` string. */
internal fun String.toColor(): Color = Color(0xFF000000 or removePrefix("#").toLong(16))
