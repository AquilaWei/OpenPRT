package org.openprt.app.map

/**
 * Colors of the markers drawn on the map, as `#RRGGBB` strings MapLibre takes. Kept apart from
 * the Compose theme because MapLibre layers need strings, and a map legend can show the same set.
 */
data class MapPalette(
    val styleUrl: String,
    val routeLine: String,
    val stop: String,
    val stopOutline: String,
    /** The bus glyph drawn on a [stop] sign. */
    val stopGlyph: String,
    val routeStop: String,
    val routeStopOutline: String,
    val boardingStop: String,
    /** The bus glyph drawn on a [boardingStop] sign. */
    val boardingStopGlyph: String,
    val destination: String,
    /** A starting point the user chose instead of their location; unlike every other marker. */
    val origin: String,
    val bus: String,
    /** The bus glyph drawn on the [bus] disc. */
    val busGlyph: String,
    val user: String,
    /** Ring around the large markers, so they stand out from the map below. */
    val markerOutline: String
)

/** OpenFreeMap styles: free, no API key; each carries the OSM attribution. */
private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// "fiord" rather than "dark": on the near-black "dark" style roads and labels were too faint to
// read on a phone (user feedback, 2026-10-02).
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/fiord"

/**
 * The map's style and marker colors for the light or [dark] theme. On the slate-blue dark map the
 * navy of the light theme would vanish, and its roads are mid blue-gray, so routes and stops turn
 * a pale blue much lighter than the roads, and the small stop dots get a dark ring.
 */
fun mapPalette(dark: Boolean): MapPalette = if (dark) {
    MapPalette(
        styleUrl = DARK_STYLE_URL,
        routeLine = "#D5E3FF",
        stop = "#D5E3FF",
        stopOutline = "#111318",
        // White would be too faint on the pale blue sign.
        stopGlyph = "#111318",
        routeStop = "#111318",
        routeStopOutline = "#D5E3FF",
        boardingStop = "#FFC72C",
        boardingStopGlyph = "#111318",
        destination = "#F28B82",
        origin = "#CE93D8",
        bus = "#81C995",
        // White would be too faint on the pale green disc.
        busGlyph = "#0D3B1E",
        user = "#4C8DF6",
        markerOutline = "#FFFFFF"
    )
} else {
    MapPalette(
        styleUrl = LIGHT_STYLE_URL,
        routeLine = "#17365F",
        stop = "#17365F",
        stopOutline = "#FFFFFF",
        stopGlyph = "#FFFFFF",
        routeStop = "#FFFFFF",
        routeStopOutline = "#17365F",
        boardingStop = "#F2A900",
        // Navy rather than white: white on gold is below 3:1.
        boardingStopGlyph = "#17365F",
        destination = "#D93025",
        origin = "#8E24AA",
        bus = "#188038",
        busGlyph = "#FFFFFF",
        user = "#1A73E8",
        markerOutline = "#FFFFFF"
    )
}
