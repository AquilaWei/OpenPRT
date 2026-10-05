package org.openprt.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import kotlin.math.acos
import org.openprt.app.geo.LatLng

/**
 * The places the camera frames before a route or trip is shown: the chosen [origin] and the
 * [destination], or [center] (the user) in place of an origin that is not set. A single point
 * means there is nothing to frame and the camera just follows it.
 */
internal fun cameraPoints(center: LatLng, origin: LatLng?, destination: LatLng?): List<LatLng> =
    listOfNotNull(origin ?: center, destination).distinct()

private const val PIN_WIDTH_DP = 28f
private const val PIN_HEIGHT_DP = 38f
private const val PIN_RING_DP = 2.5f

/**
 * A map pin, a disc of [fill] narrowing to a point with a [ring] around it and a dot of [ring]
 * in the middle: the shape riders know as "this place". The layer anchors it at the bottom, so
 * the point, not the middle of the bitmap, sits on the place.
 */
internal fun pinBitmap(context: Context, fill: String, ring: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = PIN_WIDTH_DP * density
    val height = PIN_HEIGHT_DP * density
    val ringWidth = PIN_RING_DP * density
    val center = width / 2
    val radius = center - ringWidth
    val tipY = height - ringWidth
    // The two lines from the tip meet the disc where they touch it, so the outline is smooth.
    val tangent = Math.toDegrees(acos(radius / (tipY - center)).toDouble()).toFloat()
    val pin = Path().apply {
        moveTo(center, tipY)
        arcTo(
            RectF(center - radius, center - radius, center + radius, center + radius),
            90f + tangent,
            360f - 2 * tangent
        )
        close()
    }
    val bitmap = createBitmap(width.toInt(), height.toInt())
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2 * ringWidth
    paint.strokeJoin = Paint.Join.ROUND
    paint.color = ring.toColorInt()
    canvas.drawPath(pin, paint)
    paint.style = Paint.Style.FILL
    paint.color = fill.toColorInt()
    canvas.drawPath(pin, paint)
    paint.color = ring.toColorInt()
    canvas.drawCircle(center, center, radius * DOT_FRACTION, paint)
    return bitmap
}

private const val DOT_FRACTION = 0.38f
