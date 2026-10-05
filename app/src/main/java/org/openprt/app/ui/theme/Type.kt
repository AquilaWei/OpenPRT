package org.openprt.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Apple-style type: semibold titles with slightly tighter tracking, regular body text. The
// phone's own font is used; SF Pro may not be bundled outside Apple platforms.
private val Default = Typography()

internal val OpenPrtTypography = Typography(
    headlineSmall = Default.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = Default.labelMedium.copy(letterSpacing = 0.sp),
    bodyLarge = Default.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = Default.bodyMedium.copy(letterSpacing = 0.sp)
)

// iOS-like rounded corners: cards about 12 dp, sheets and floating panels larger.
internal val OpenPrtShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
