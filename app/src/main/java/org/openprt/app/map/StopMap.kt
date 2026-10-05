package org.openprt.app.map

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.openprt.app.details.BusPosition
import org.openprt.app.details.RouteShape
import org.openprt.app.geo.LatLng
import org.openprt.app.trip.TripMapLayers

private const val STOPS_SOURCE = "stops"
private const val USER_SOURCE = "user-location"
private const val ROUTE_LINE_SOURCE = "route-line"
private const val ROUTE_STOPS_SOURCE = "route-stops"
private const val BOARDING_STOP_SOURCE = "boarding-stop"
private const val BUS_SOURCE = "bus"
private const val DESTINATION_SOURCE = "destination"
private const val SELECTED_STOP_SOURCE = "selected-stop"
private const val TRIP_WALK_SOURCE = "trip-walks"
private const val TRIP_RIDE_SOURCE = "trip-rides"
private const val TRIP_BOARDING_SOURCE = "trip-boarding"
private const val TRIP_ALIGHTING_SOURCE = "trip-alighting"
private const val BUS_BADGE_IMAGE = "bus-badge"
private const val BUS_HEADING_IMAGE = "bus-heading"
private const val STOP_SIGN_IMAGE = "stop-sign"
private const val BOARDING_STOP_SIGN_IMAGE = "boarding-stop-sign"

// Wider than the bus badge (18 dp with its ring), so a bus on top of the rider leaves a rim.
private const val USER_HALO_RADIUS = 26f
private const val USER_HALO_OPACITY = 0.25f

// Street level: a 400 m stop radius fills most of a phone screen.
private const val FOLLOW_ZOOM = 16.0

// Half a 48 dp touch target, so a stop dot is as easy to hit as a button.
private val STOP_TOUCH_RADIUS = 24.dp

/** Margin kept between a fitted route (or user and destination) and the map edges. */
private val ROUTE_FIT_PADDING = 48.dp

/**
 * MapLibre map showing [stops] and, when known, the device position [userLocation]. The camera
 * moves to [center] each time it changes, so the map follows the location.
 *
 * While a [route] is shown, the camera is fitted to it instead and stops following [center];
 * the route's line, its stops and the highlighted boarding stop are drawn under the user dot.
 * The selected [bus], when reported, is drawn on top of everything, the user dot included, as a
 * bus badge with an arrow pointing where it is heading; the camera does not follow it.
 *
 * A [destination], when set, is drawn as a red dot and, while no route is shown, the camera fits
 * both [center] and the destination instead of zooming in on [center]. Long-pressing the map
 * reports the pressed spot through [onLongPress].
 *
 * Tapping near one of [stops] or a stop of [route] reports that stop through [onStopClick];
 * [selectedStop], the stop whose buses are shown, is drawn large like a boarding stop.
 *
 * A chosen [trip] is drawn the same way as a route, with its walks dashed; the camera is fitted
 * to it while no route is shown. Camera fits keep [overlayPadding] clear, the space the search
 * box covers.
 *
 * [palette] sets the map style and marker colors; a new palette (the theme changed) reloads the
 * style, which drops every layer, so the markers are added and filled in again.
 *
 * Needs the native MapLibre library, so it does not run under Robolectric; screen tests pass a
 * stand-in instead.
 */
@Composable
fun StopMap(
    center: LatLng?,
    userLocation: LatLng?,
    stops: List<StopMarker>,
    route: RouteShape?,
    bus: BusPosition?,
    destination: LatLng?,
    onLongPress: (LatLng) -> Unit,
    palette: MapPalette,
    modifier: Modifier = Modifier,
    trip: TripMapLayers? = null,
    overlayPadding: PaddingValues = PaddingValues(),
    selectedStop: LatLng? = null,
    onStopClick: (StopMarker) -> Unit = {}
) {
    val context = LocalContext.current
    val fitPadding = fitPaddingPx(overlayPadding)
    val touchRadiusPx = with(LocalDensity.current) { STOP_TOUCH_RADIUS.toPx() }
    val mapView = remember { createMapView(context) }
    // Null until the style has loaded; sources can only be updated after that.
    var style by remember { mutableStateOf<Style?>(null) }
    // The listener is registered once; this keeps it calling the latest callback.
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnStopClick by rememberUpdatedState(onStopClick)
    val tappableStops by rememberUpdatedState(stops + route?.stops.orEmpty())

    MapViewLifecycle(mapView)
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.addOnMapLongClickListener { point ->
                currentOnLongPress(LatLng(point.latitude, point.longitude))
                true
            }
            // A tap away from every stop is not consumed, so the map handles it as usual.
            map.addOnMapClickListener { point ->
                val projection = map.projection
                val tap = projection.toScreenLocation(point)
                val stop = stopAt(ScreenPoint(tap.x, tap.y), tappableStops, touchRadiusPx) {
                    val screen = projection.toScreenLocation(it.toMapLibre())
                    ScreenPoint(screen.x, screen.y)
                }
                stop?.let(currentOnStopClick)
                stop != null
            }
        }
    }
    LaunchedEffect(mapView, palette) {
        // Cleared first so nothing writes to the old style's sources while the new one loads.
        style = null
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(palette.styleUrl)) { loaded ->
                addMarkerLayers(context, loaded, palette)
                style = loaded
            }
        }
    }

    LaunchedEffect(style, stops) {
        style?.setPoints(STOPS_SOURCE, stops.map { it.position })
    }
    LaunchedEffect(style, route) {
        val loaded = style ?: return@LaunchedEffect
        loaded.setLines(ROUTE_LINE_SOURCE, listOfNotNull(route?.line))
        loaded.setPoints(ROUTE_STOPS_SOURCE, route?.stops.orEmpty().map { it.position })
        loaded.setPoints(BOARDING_STOP_SOURCE, listOfNotNull(route?.boardingStop?.position))
    }
    LaunchedEffect(style, trip) {
        val loaded = style ?: return@LaunchedEffect
        loaded.setLines(TRIP_WALK_SOURCE, trip?.walks.orEmpty())
        loaded.setLines(TRIP_RIDE_SOURCE, trip?.rides.orEmpty())
        loaded.setPoints(TRIP_BOARDING_SOURCE, trip?.boardingStops.orEmpty())
        loaded.setPoints(TRIP_ALIGHTING_SOURCE, trip?.alightingStops.orEmpty())
    }
    LaunchedEffect(style, bus) {
        style?.getSourceAs<GeoJsonSource>(BUS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(bus?.toFeature()))
        )
    }
    LaunchedEffect(style, selectedStop) {
        style?.setPoints(SELECTED_STOP_SOURCE, listOfNotNull(selectedStop))
    }
    LaunchedEffect(style, destination) {
        style?.setPoints(DESTINATION_SOURCE, listOfNotNull(destination))
    }
    LaunchedEffect(style, userLocation) {
        style?.setPoints(USER_SOURCE, listOfNotNull(userLocation))
    }
    // Keyed on whether a route or trip is shown, so closing it moves back to the user. With a
    // destination it is also keyed on the space kept clear at the top: picking a place closes
    // the search results that covered the map, and the fit has to be redone without them.
    LaunchedEffect(
        center,
        destination,
        route == null,
        trip == null,
        fitPadding.top.takeIf { destination != null }
    ) {
        if (center == null || route != null || trip != null) return@LaunchedEffect
        val update = if (destination == null || destination == center) {
            CameraUpdateFactory.newLatLngZoom(center.toMapLibre(), FOLLOW_ZOOM)
        } else {
            val bounds = LatLngBounds.Builder()
                .include(center.toMapLibre())
                .include(destination.toMapLibre())
                .build()
            fitPadding.boundsUpdate(bounds)
        }
        mapView.getMapAsync { map -> map.animateCamera(update) }
    }
    LaunchedEffect(route) {
        mapView.fitTo(route?.line.orEmpty(), fitPadding)
    }
    // Keyed on the trip's points only, so filling in its ride lines does not move the camera.
    LaunchedEffect(trip?.allPoints?.firstOrNull(), trip?.allPoints?.lastOrNull(), route == null) {
        if (route == null) mapView.fitTo(trip?.allPoints.orEmpty(), fitPadding)
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun createMapView(context: Context): MapView {
    // Must run before the first MapView is created; later calls return the same instance.
    MapLibre.getInstance(context)
    return MapView(context).apply { onCreate(null) }
}

/** MapView needs the host's lifecycle forwarded to it to load tiles and release GL resources. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
}

/** Pixels to keep clear on each side when fitting the camera: [overlay] plus a margin. */
private class FitPadding(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun boundsUpdate(bounds: LatLngBounds) =
        CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom)
}

@Composable
private fun fitPaddingPx(overlay: PaddingValues): FitPadding {
    val direction = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        FitPadding(
            left = (overlay.calculateLeftPadding(direction) + ROUTE_FIT_PADDING).roundToPx(),
            top = (overlay.calculateTopPadding() + ROUTE_FIT_PADDING).roundToPx(),
            right = (overlay.calculateRightPadding(direction) + ROUTE_FIT_PADDING).roundToPx(),
            bottom = (overlay.calculateBottomPadding() + ROUTE_FIT_PADDING).roundToPx()
        )
    }
}

/** Fits the camera to [points]; LatLngBounds needs two distinct points, so fewer do nothing. */
private fun MapView.fitTo(points: List<LatLng>, padding: FitPadding) {
    val distinct = points.distinct().takeIf { it.size >= 2 } ?: return
    val bounds = LatLngBounds.Builder().includes(distinct.map { it.toMapLibre() }).build()
    getMapAsync { map -> map.animateCamera(padding.boundsUpdate(bounds)) }
}

private fun Style.setLines(sourceId: String, lines: List<List<LatLng>>) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(
        FeatureCollection.fromFeatures(
            lines.map { line ->
                Feature.fromGeometry(LineString.fromLngLats(line.map { it.toPoint() }))
            }
        )
    )
}

private fun Style.setPoints(sourceId: String, points: List<LatLng>) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(
        FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it.toPoint()) })
    )
}

/**
 * Layers are drawn in the order added: route line, stops, route stops, trip, boarding stop,
 * selected stop, destination, user, bus.
 */
private fun addMarkerLayers(context: Context, style: Style, palette: MapPalette) {
    listOf(
        ROUTE_LINE_SOURCE,
        STOPS_SOURCE,
        ROUTE_STOPS_SOURCE,
        TRIP_WALK_SOURCE,
        TRIP_RIDE_SOURCE,
        TRIP_ALIGHTING_SOURCE,
        TRIP_BOARDING_SOURCE,
        BOARDING_STOP_SOURCE,
        SELECTED_STOP_SOURCE,
        DESTINATION_SOURCE,
        BUS_SOURCE,
        USER_SOURCE
    ).forEach { style.addSource(GeoJsonSource(it)) }
    style.addLayer(
        LineLayer("route-line-layer", ROUTE_LINE_SOURCE).withProperties(
            lineColor(palette.routeLine),
            lineWidth(5f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    // Stop signs rather than dots, so a stop reads as a bus stop at a glance (user feedback,
    // 2026-10-05). Stops along a route line stay dots: a sign at every one would bury the line.
    style.addImage(
        STOP_SIGN_IMAGE,
        stopSignBitmap(context, STOP_SIGN_DP, palette.stop, palette.stopOutline, palette.stopGlyph)
    )
    style.addImage(
        BOARDING_STOP_SIGN_IMAGE,
        stopSignBitmap(
            context,
            LARGE_STOP_SIGN_DP,
            palette.boardingStop,
            palette.markerOutline,
            palette.boardingStopGlyph
        )
    )
    style.addLayer(stopSignLayer("stops-layer", STOPS_SOURCE, STOP_SIGN_IMAGE))
    style.addLayer(
        CircleLayer("route-stops-layer", ROUTE_STOPS_SOURCE).withProperties(
            circleRadius(4f),
            circleColor(palette.routeStop),
            circleStrokeColor(palette.routeStopOutline),
            circleStrokeWidth(2f)
        )
    )
    addTripLayers(style, palette)
    // Larger and gold so the stop to walk to stands out from the rest of the route.
    style.addLayer(
        stopSignLayer("boarding-stop-layer", BOARDING_STOP_SOURCE, BOARDING_STOP_SIGN_IMAGE)
    )
    // The tapped stop looks like a boarding stop: it is where the listed buses are boarded.
    style.addLayer(
        stopSignLayer("selected-stop-layer", SELECTED_STOP_SOURCE, BOARDING_STOP_SIGN_IMAGE)
    )
    // Red, the usual map color for "where you are going".
    style.addLayer(
        largeMarkerLayer("destination-layer", DESTINATION_SOURCE, palette.destination, palette)
    )
    // Above the stops but under the bus: when the bus reaches the rider, the bus is what they
    // are watching. The halo is wider than the bus badge, so the rider still shows around it.
    style.addLayer(
        CircleLayer("user-halo-layer", USER_SOURCE).withProperties(
            circleRadius(USER_HALO_RADIUS),
            circleColor(palette.user),
            circleOpacity(USER_HALO_OPACITY)
        )
    )
    style.addLayer(
        CircleLayer("user-location-layer", USER_SOURCE).withProperties(
            circleRadius(8f),
            circleColor(palette.user),
            circleStrokeColor(palette.markerOutline),
            circleStrokeWidth(3f)
        )
    )
    // A bus badge rather than another dot, so it reads as a bus; the arrow under it turns with
    // the bus's heading while the badge stays upright.
    style.addImage(BUS_HEADING_IMAGE, busHeadingBitmap(context, palette))
    style.addImage(BUS_BADGE_IMAGE, busBadgeBitmap(context, palette))
    style.addLayer(
        SymbolLayer("bus-heading-layer", BUS_SOURCE).withProperties(
            iconImage(BUS_HEADING_IMAGE),
            iconRotate(Expression.get(HEADING_PROPERTY)),
            iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )
    )
    style.addLayer(
        SymbolLayer("bus-layer", BUS_SOURCE).withProperties(
            iconImage(BUS_BADGE_IMAGE),
            iconAllowOverlap(true),
            iconIgnorePlacement(true)
        )
    )
}

/** A trip's rides like a route line, its walks dashed in the user's color, and its stops. */
private fun addTripLayers(style: Style, palette: MapPalette) {
    style.addLayer(
        LineLayer("trip-walk-layer", TRIP_WALK_SOURCE).withProperties(
            lineColor(palette.user),
            lineWidth(4f),
            lineDasharray(arrayOf(1f, 1.5f)),
            lineCap(Property.LINE_CAP_ROUND)
        )
    )
    style.addLayer(
        LineLayer("trip-ride-layer", TRIP_RIDE_SOURCE).withProperties(
            lineColor(palette.routeLine),
            lineWidth(6f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        CircleLayer("trip-alighting-layer", TRIP_ALIGHTING_SOURCE).withProperties(
            circleRadius(7f),
            circleColor(palette.routeStop),
            circleStrokeColor(palette.routeStopOutline),
            circleStrokeWidth(3f)
        )
    )
    style.addLayer(
        stopSignLayer("trip-boarding-layer", TRIP_BOARDING_SOURCE, BOARDING_STOP_SIGN_IMAGE)
    )
}

/** Every stop sign is drawn, even crowded together downtown, since each one can be tapped. */
private fun stopSignLayer(id: String, source: String, image: String) =
    SymbolLayer(id, source).withProperties(
        iconImage(image),
        iconAllowOverlap(true),
        iconIgnorePlacement(true)
    )

private fun largeMarkerLayer(id: String, source: String, color: String, palette: MapPalette) =
    CircleLayer(id, source).withProperties(
        circleRadius(10f),
        circleColor(color),
        circleStrokeColor(palette.markerOutline),
        circleStrokeWidth(3f)
    )

private fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun LatLng.toMapLibre() = org.maplibre.android.geometry.LatLng(latitude, longitude)
