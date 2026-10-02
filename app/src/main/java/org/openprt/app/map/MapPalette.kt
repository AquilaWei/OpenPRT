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
    val routeStop: String,
    val routeStopOutline: String,
    val boardingStop: String,
    val destination: String,
    val bus: String,
    val user: String,
    /** Ring around the large markers, so they stand out from the map below. */
    val markerOutline: String
)

/** OpenFreeMap styles: free, no API key; each carries the OSM attribution. */
private const val LIGHT_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val DARK_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

/**
 * The map's style and marker colors for the light or [dark] theme. On the dark map the navy of
 * the light theme would vanish, so routes and stops turn light blue and the small stop dots get a
 * dark ring instead of a white one.
 */
fun mapPalette(dark: Boolean): MapPalette = if (dark) {
    MapPalette(
        styleUrl = DARK_STYLE_URL,
        routeLine = "#8AB4F8",
        stop = "#A8C8FF",
        stopOutline = "#111318",
        routeStop = "#111318",
        routeStopOutline = "#A8C8FF",
        boardingStop = "#FFC72C",
        destination = "#F28B82",
        bus = "#81C995",
        user = "#4C8DF6",
        markerOutline = "#FFFFFF"
    )
} else {
    MapPalette(
        styleUrl = LIGHT_STYLE_URL,
        routeLine = "#17365F",
        stop = "#17365F",
        stopOutline = "#FFFFFF",
        routeStop = "#FFFFFF",
        routeStopOutline = "#17365F",
        boardingStop = "#F2A900",
        destination = "#D93025",
        bus = "#188038",
        user = "#1A73E8",
        markerOutline = "#FFFFFF"
    )
}
