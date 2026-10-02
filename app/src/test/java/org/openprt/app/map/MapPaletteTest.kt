package org.openprt.app.map

import org.junit.Assert.assertEquals
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
}
