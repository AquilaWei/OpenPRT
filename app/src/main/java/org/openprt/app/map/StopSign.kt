package org.openprt.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import org.openprt.app.R

/** Outer size of a nearby stop's sign, ring included; about the size of the old stop dot's tap target. */
internal const val STOP_SIGN_DP = 22

/** The boarding or tapped stop is a bigger sign, so it stands out among the nearby ones. */
internal const val LARGE_STOP_SIGN_DP = 30

/**
 * A bus stop sign: a rounded square of [fill] with a [ring] and a bus glyph in [glyph], the
 * shape riders know from other map apps. Square, unlike the round bus badge, so a stop and the
 * bus at it never look alike.
 */
internal fun stopSignBitmap(
    context: Context,
    sizeDp: Int,
    fill: String,
    ring: String,
    glyph: String
): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = sizeDp * density
    val ringWidth = size * RING_FRACTION
    val bitmap = createBitmap(size.toInt(), size.toInt())
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val corner = size * CORNER_FRACTION
    paint.color = ring.toColorInt()
    canvas.drawRoundRect(0f, 0f, size, size, corner, corner, paint)
    paint.color = fill.toColorInt()
    canvas.drawRoundRect(
        ringWidth,
        ringWidth,
        size - ringWidth,
        size - ringWidth,
        corner - ringWidth,
        corner - ringWidth,
        paint
    )
    val icon = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_bus)).mutate()
    icon.setTint(glyph.toColorInt())
    val inset = (size * GLYPH_INSET_FRACTION).toInt()
    icon.setBounds(inset, inset, size.toInt() - inset, size.toInt() - inset)
    icon.draw(canvas)
    return bitmap
}

// Proportions rather than dp, so the small and large signs look the same.
private const val RING_FRACTION = 0.1f
private const val CORNER_FRACTION = 0.25f
private const val GLYPH_INSET_FRACTION = 0.2f
