package org.openprt.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG AA asks for a contrast ratio of at least 4.5:1 for normal text. Each test checks one
 * foreground / background pair the screens actually use.
 */
class ThemeContrastTest {
    @Test
    fun light_bodyTextOnSurface_meetsAA() {
        assertMeetsAA(LightColors.onSurface, LightColors.surface)
    }

    @Test
    fun light_secondaryTextOnSurface_meetsAA() {
        assertMeetsAA(LightColors.onSurfaceVariant, LightColors.surface)
    }

    @Test
    fun light_textButtonOnSurface_meetsAA() {
        assertMeetsAA(LightColors.primary, LightColors.surface)
    }

    @Test
    fun light_routeBadgeText_meetsAA() {
        assertMeetsAA(LightColors.onPrimary, LightColors.primary)
    }

    @Test
    fun light_liveText_meetsAA() {
        assertMeetsAA(LightColors.tertiary, LightColors.surface)
    }

    @Test
    fun light_delayedText_meetsAA() {
        assertMeetsAA(LightColors.error, LightColors.surface)
    }

    @Test
    fun light_topBarTitle_meetsAA() {
        assertMeetsAA(LightBrandColors.onAppBar, LightBrandColors.appBar)
    }

    @Test
    fun light_accentButtonIcon_meetsAA() {
        assertMeetsAA(LightBrandColors.onAccent, LightBrandColors.accent)
    }

    @Test
    fun dark_bodyTextOnSurface_meetsAA() {
        assertMeetsAA(DarkColors.onSurface, DarkColors.surface)
    }

    @Test
    fun dark_secondaryTextOnSurface_meetsAA() {
        assertMeetsAA(DarkColors.onSurfaceVariant, DarkColors.surface)
    }

    @Test
    fun dark_textButtonOnSurface_meetsAA() {
        assertMeetsAA(DarkColors.primary, DarkColors.surface)
    }

    @Test
    fun dark_routeBadgeText_meetsAA() {
        assertMeetsAA(DarkColors.onPrimary, DarkColors.primary)
    }

    @Test
    fun dark_liveText_meetsAA() {
        assertMeetsAA(DarkColors.tertiary, DarkColors.surface)
    }

    @Test
    fun dark_delayedText_meetsAA() {
        assertMeetsAA(DarkColors.error, DarkColors.surface)
    }

    @Test
    fun dark_topBarTitle_meetsAA() {
        assertMeetsAA(DarkBrandColors.onAppBar, DarkBrandColors.appBar)
    }

    @Test
    fun dark_accentButtonIcon_meetsAA() {
        assertMeetsAA(DarkBrandColors.onAccent, DarkBrandColors.accent)
    }

    @Test
    fun light_bottomSheetText_meetsAA() {
        // The bottom sheet is drawn on surfaceContainerLow.
        assertMeetsAA(LightColors.onSurfaceVariant, LightColors.surfaceContainerLow)
    }

    @Test
    fun dark_bottomSheetText_meetsAA() {
        assertMeetsAA(DarkColors.onSurfaceVariant, DarkColors.surfaceContainerLow)
    }

    @Test
    fun light_minutesPillText_meetsAA() {
        assertMeetsAA(LightColors.onPrimaryContainer, LightColors.primaryContainer)
    }

    @Test
    fun light_liveChipText_meetsAA() {
        assertMeetsAA(LightColors.onTertiaryContainer, LightColors.tertiaryContainer)
    }

    @Test
    fun light_delayedChipText_meetsAA() {
        assertMeetsAA(LightColors.onErrorContainer, LightColors.errorContainer)
    }

    @Test
    fun light_scheduledChipText_meetsAA() {
        assertMeetsAA(LightColors.onSurfaceVariant, LightColors.surfaceVariant)
    }

    @Test
    fun light_boardHereChipText_meetsAA() {
        assertMeetsAA(LightColors.onSecondaryContainer, LightColors.secondaryContainer)
    }

    @Test
    fun dark_minutesPillText_meetsAA() {
        assertMeetsAA(DarkColors.onPrimaryContainer, DarkColors.primaryContainer)
    }

    @Test
    fun dark_liveChipText_meetsAA() {
        assertMeetsAA(DarkColors.onTertiaryContainer, DarkColors.tertiaryContainer)
    }

    @Test
    fun dark_delayedChipText_meetsAA() {
        assertMeetsAA(DarkColors.onErrorContainer, DarkColors.errorContainer)
    }

    @Test
    fun dark_scheduledChipText_meetsAA() {
        assertMeetsAA(DarkColors.onSurfaceVariant, DarkColors.surfaceVariant)
    }

    @Test
    fun dark_boardHereChipText_meetsAA() {
        assertMeetsAA(DarkColors.onSecondaryContainer, DarkColors.secondaryContainer)
    }

    private fun assertMeetsAA(foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("contrast $ratio:1 is below 4.5:1", ratio >= 4.5f)
    }
}
