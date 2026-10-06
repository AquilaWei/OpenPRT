package org.openprt.app.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPaletteTest {
    @Test
    fun mapPalette_light_usesLibertyStyle() {
        assertEquals(
            "https://tiles.openfreemap.org/styles/liberty",
            mapPalette(dark = false).styleUrl
        )
    }

    @Test
    fun mapPalette_dark_usesFiordStyle() {
        assertEquals("https://tiles.openfreemap.org/styles/fiord", mapPalette(dark = true).styleUrl)
    }

    @Test
    fun mapPalette_light_drawsRoutesAndStopsInNavy() {
        val palette = mapPalette(dark = false)

        assertEquals(listOf("#17365F", "#17365F"), listOf(palette.routeLine, palette.stop))
    }

    @Test
    fun mapPalette_dark_drawsRoutesAndStopsInPaleBlue() {
        val palette = mapPalette(dark = true)

        assertEquals(listOf("#D5E3FF", "#D5E3FF"), listOf(palette.routeLine, palette.stop))
    }

    @Test
    fun mapPalette_dark_ringsStopsInMapBackgroundColor() {
        assertEquals("#111318", mapPalette(dark = true).stopOutline)
    }

    @Test
    fun mapPalette_light_marksBoardingStopGold() {
        assertEquals("#F2A900", mapPalette(dark = false).boardingStop)
    }

    @Test
    fun mapPalette_dark_marksBoardingStopGold() {
        assertEquals("#FFC72C", mapPalette(dark = true).boardingStop)
    }

    // WCAG asks 3:1 for icons and other non-text graphics.
    @Test
    fun mapPalette_light_busGlyphStandsOutOnBusDisc() {
        val palette = mapPalette(dark = false)

        assertTrue(contrast(palette.busGlyph, palette.bus) >= 3.0)
    }

    @Test
    fun mapPalette_dark_busGlyphStandsOutOnBusDisc() {
        val palette = mapPalette(dark = true)

        assertTrue(contrast(palette.busGlyph, palette.bus) >= 3.0)
    }

    @Test
    fun mapPalette_light_stopGlyphStandsOutOnStopSign() {
        val palette = mapPalette(dark = false)

        assertTrue(contrast(palette.stopGlyph, palette.stop) >= 3.0)
    }

    @Test
    fun mapPalette_dark_stopGlyphStandsOutOnStopSign() {
        val palette = mapPalette(dark = true)

        assertTrue(contrast(palette.stopGlyph, palette.stop) >= 3.0)
    }

    @Test
    fun mapPalette_light_boardingStopGlyphStandsOutOnGoldSign() {
        val palette = mapPalette(dark = false)

        assertTrue(contrast(palette.boardingStopGlyph, palette.boardingStop) >= 3.0)
    }

    @Test
    fun mapPalette_dark_boardingStopGlyphStandsOutOnGoldSign() {
        val palette = mapPalette(dark = true)

        assertTrue(contrast(palette.boardingStopGlyph, palette.boardingStop) >= 3.0)
    }

    @Test
    fun mapPalette_light_originColorIsUnlikeEveryOtherMarker() {
        val palette = mapPalette(dark = false)

        assertEquals(
            emptyList<String>(),
            listOf(
                palette.user,
                palette.stop,
                palette.boardingStop,
                palette.destination,
                palette.bus,
                palette.routeLine
            ).filter { it == palette.origin }
        )
    }

    @Test
    fun mapPalette_dark_originColorIsUnlikeEveryOtherMarker() {
        val palette = mapPalette(dark = true)

        assertEquals(
            emptyList<String>(),
            listOf(
                palette.user,
                palette.stop,
                palette.boardingStop,
                palette.destination,
                palette.bus,
                palette.routeLine
            ).filter { it == palette.origin }
        )
    }

    private fun contrast(foreground: String, background: String): Double {
        val lighter = maxOf(hex(foreground).luminance(), hex(background).luminance())
        val darker = minOf(hex(foreground).luminance(), hex(background).luminance())
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun hex(color: String) = Color(0xFF000000 or color.removePrefix("#").toLong(16))
}
