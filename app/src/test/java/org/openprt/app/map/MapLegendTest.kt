package org.openprt.app.map

import org.junit.Assert.assertEquals
import org.junit.Test
import org.openprt.app.R

/** The legend must show each marker in the color its map layer is drawn with (StopMap.kt). */
class MapLegendTest {
    @Test
    fun mapLegend_light_usesMapLayerColors() {
        val palette = mapPalette(dark = false)

        assertEquals(
            listOf(
                LegendEntry(LegendSymbol.LARGE_DOT, R.string.legend_user, "#1A73E8", "#FFFFFF"),
                LegendEntry(LegendSymbol.DOT, R.string.legend_stop, "#17365F", "#FFFFFF"),
                LegendEntry(
                    LegendSymbol.LARGE_DOT,
                    R.string.legend_boarding_stop,
                    "#F2A900",
                    "#FFFFFF"
                ),
                LegendEntry(LegendSymbol.DOT, R.string.legend_route_stop, "#FFFFFF", "#17365F"),
                LegendEntry(LegendSymbol.LINE, R.string.legend_route, "#17365F"),
                LegendEntry(LegendSymbol.DASHED_LINE, R.string.legend_walk, "#1A73E8"),
                LegendEntry(LegendSymbol.BUS, R.string.legend_bus, "#188038", "#FFFFFF", "#FFFFFF"),
                LegendEntry(
                    LegendSymbol.LARGE_DOT,
                    R.string.legend_destination,
                    "#D93025",
                    "#FFFFFF"
                )
            ),
            mapLegend(palette)
        )
    }

    @Test
    fun mapLegend_dark_usesMapLayerColors() {
        val palette = mapPalette(dark = true)

        assertEquals(
            listOf(
                LegendEntry(LegendSymbol.LARGE_DOT, R.string.legend_user, "#4C8DF6", "#FFFFFF"),
                LegendEntry(LegendSymbol.DOT, R.string.legend_stop, "#D5E3FF", "#111318"),
                LegendEntry(
                    LegendSymbol.LARGE_DOT,
                    R.string.legend_boarding_stop,
                    "#FFC72C",
                    "#FFFFFF"
                ),
                LegendEntry(LegendSymbol.DOT, R.string.legend_route_stop, "#111318", "#D5E3FF"),
                LegendEntry(LegendSymbol.LINE, R.string.legend_route, "#D5E3FF"),
                LegendEntry(LegendSymbol.DASHED_LINE, R.string.legend_walk, "#4C8DF6"),
                LegendEntry(LegendSymbol.BUS, R.string.legend_bus, "#81C995", "#FFFFFF", "#0D3B1E"),
                LegendEntry(
                    LegendSymbol.LARGE_DOT,
                    R.string.legend_destination,
                    "#F28B82",
                    "#FFFFFF"
                )
            ),
            mapLegend(palette)
        )
    }
}
