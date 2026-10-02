package org.openprt.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.openprt.app.R
import org.openprt.app.details.BusPosition

/** Feature property holding the bus's heading, read by the map's arrow layer to rotate it. */
internal const val HEADING_PROPERTY = "heading"

/**
 * The bus as a map point carrying its heading in degrees clockwise from north, as TrueTime
 * reports it, so the arrow layer can point where the bus is going.
 */
internal fun BusPosition.toFeature(): Feature =
    Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addNumberProperty(HEADING_PROPERTY, headingDegrees)
    }

// Both images share one square size and center, so the arrow sits just outside the badge.
private const val IMAGE_DP = 48
private const val BADGE_RADIUS_DP = 15f
private const val RING_DP = 3f
private const val GLYPH_DP = 20

/**
 * The bus badge: a round [MapPalette.bus] disc with a white ring and a bus glyph. It stays
 * upright, since a bus picture turned upside down when heading south is harder to read.
 */
internal fun busBadgeBitmap(context: Context, palette: MapPalette): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (IMAGE_DP * density).toInt()
    val bitmap = createBitmap(size, size)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = palette.markerOutline.toColorInt()
    canvas.drawCircle(center, center, (BADGE_RADIUS_DP + RING_DP) * density, paint)
    paint.color = palette.bus.toColorInt()
    canvas.drawCircle(center, center, BADGE_RADIUS_DP * density, paint)
    val glyph = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_bus)).mutate()
    glyph.setTint(palette.busGlyph.toColorInt())
    val half = (GLYPH_DP * density / 2).toInt()
    glyph.setBounds(
        center.toInt() - half,
        center.toInt() - half,
        center.toInt() + half,
        center.toInt() + half
    )
    glyph.draw(canvas)
    return bitmap
}

/**
 * A triangle pointing up just outside the badge; the map rotates it by the bus's heading, so it
 * shows the direction of travel around the upright badge.
 */
internal fun busHeadingBitmap(context: Context, palette: MapPalette): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (IMAGE_DP * density).toInt()
    val bitmap = createBitmap(size, size)
    val center = size / 2f
    val tip = 1f * density
    val base = (IMAGE_DP / 2 - BADGE_RADIUS_DP - RING_DP + 2f) * density
    val halfWidth = 7f * density
    val arrow = Path().apply {
        moveTo(center, tip)
        lineTo(center + halfWidth, base)
        lineTo(center - halfWidth, base)
        close()
    }
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.FILL
    paint.color = palette.bus.toColorInt()
    canvas.drawPath(arrow, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.5f * density
    paint.color = palette.markerOutline.toColorInt()
    canvas.drawPath(arrow, paint)
    return bitmap
}
