package org.openprt.app.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.openprt.app.details.RouteShape
import org.openprt.app.geo.LatLng

/** OpenFreeMap's OSM-based style: free, no API key; the style carries the OSM attribution. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val STOPS_SOURCE = "stops"
private const val USER_SOURCE = "user-location"
private const val ROUTE_LINE_SOURCE = "route-line"
private const val ROUTE_STOPS_SOURCE = "route-stops"
private const val BOARDING_STOP_SOURCE = "boarding-stop"

// Street level: a 400 m stop radius fills most of a phone screen.
private const val FOLLOW_ZOOM = 16.0

/** Margin kept between a fitted route and the map edges. */
private val ROUTE_FIT_PADDING = 48.dp

/**
 * MapLibre map showing [stops] and, when known, the device position [userLocation]. The camera
 * moves to [center] each time it changes, so the map follows the location.
 *
 * While a [route] is shown, the camera is fitted to it instead and stops following [center];
 * the route's line, its stops and the highlighted boarding stop are drawn under the user dot.
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val routeFitPaddingPx = with(LocalDensity.current) { ROUTE_FIT_PADDING.roundToPx() }
    val mapView = remember { createMapView(context) }
    // Null until the style has loaded; sources can only be updated after that.
    var style by remember { mutableStateOf<Style?>(null) }

    MapViewLifecycle(mapView)
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addMarkerLayers(loaded)
                style = loaded
            }
        }
    }

    LaunchedEffect(style, stops) {
        style?.setPoints(STOPS_SOURCE, stops.map { it.position })
    }
    LaunchedEffect(style, route) {
        val loaded = style ?: return@LaunchedEffect
        loaded.getSourceAs<GeoJsonSource>(ROUTE_LINE_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    route?.line?.let { line ->
                        Feature.fromGeometry(LineString.fromLngLats(line.map { it.toPoint() }))
                    }
                )
            )
        )
        loaded.setPoints(ROUTE_STOPS_SOURCE, route?.stops.orEmpty().map { it.position })
        loaded.setPoints(BOARDING_STOP_SOURCE, listOfNotNull(route?.boardingStop?.position))
    }
    LaunchedEffect(style, userLocation) {
        style?.setPoints(USER_SOURCE, listOfNotNull(userLocation))
    }
    // Keyed on whether a route is shown, so closing the route moves back to the user.
    LaunchedEffect(center, route == null) {
        if (center == null || route != null) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(center.toMapLibre(), FOLLOW_ZOOM))
        }
    }
    LaunchedEffect(route) {
        // LatLngBounds needs two distinct points; real patterns always have many.
        val line = route?.line?.distinct()?.takeIf { it.size >= 2 } ?: return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(line.map { it.toMapLibre() }).build()
        mapView.getMapAsync { map ->
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, routeFitPaddingPx))
        }
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

private fun Style.setPoints(sourceId: String, points: List<LatLng>) {
    getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(
        FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it.toPoint()) })
    )
}

/** Layers are drawn in the order added: route line, stops, route stops, boarding stop, user. */
private fun addMarkerLayers(style: Style) {
    listOf(
        ROUTE_LINE_SOURCE,
        STOPS_SOURCE,
        ROUTE_STOPS_SOURCE,
        BOARDING_STOP_SOURCE,
        USER_SOURCE
    ).forEach { style.addSource(GeoJsonSource(it)) }
    style.addLayer(
        LineLayer("route-line-layer", ROUTE_LINE_SOURCE).withProperties(
            lineColor("#0B5394"),
            lineWidth(5f),
            lineCap(Property.LINE_CAP_ROUND),
            lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        CircleLayer("stops-layer", STOPS_SOURCE).withProperties(
            circleRadius(6f),
            circleColor("#0B5394"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2f)
        )
    )
    style.addLayer(
        CircleLayer("route-stops-layer", ROUTE_STOPS_SOURCE).withProperties(
            circleRadius(4f),
            circleColor("#FFFFFF"),
            circleStrokeColor("#0B5394"),
            circleStrokeWidth(2f)
        )
    )
    // Larger and orange so the stop to walk to stands out from the rest of the route.
    style.addLayer(
        CircleLayer("boarding-stop-layer", BOARDING_STOP_SOURCE).withProperties(
            circleRadius(10f),
            circleColor("#E8710A"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f)
        )
    )
    // Added last so the user dot is drawn above the stops.
    style.addLayer(
        CircleLayer("user-location-layer", USER_SOURCE).withProperties(
            circleRadius(8f),
            circleColor("#1A73E8"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(3f)
        )
    )
}

private fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

private fun LatLng.toMapLibre() = org.maplibre.android.geometry.LatLng(latitude, longitude)
