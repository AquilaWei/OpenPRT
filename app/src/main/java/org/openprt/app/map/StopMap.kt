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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.openprt.app.geo.LatLng

/** OpenFreeMap's OSM-based style: free, no API key; the style carries the OSM attribution. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val STOPS_SOURCE = "stops"
private const val USER_SOURCE = "user-location"

// Street level: a 400 m stop radius fills most of a phone screen.
private const val FOLLOW_ZOOM = 16.0

/**
 * MapLibre map showing [stops] and, when known, the device position [userLocation]. The camera
 * moves to [center] each time it changes, so the map follows the location. Needs the native
 * MapLibre library, so it does not run under Robolectric; screen tests pass a stand-in instead.
 */
@Composable
fun StopMap(
    center: LatLng?,
    userLocation: LatLng?,
    stops: List<StopMarker>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
        style?.getSourceAs<GeoJsonSource>(STOPS_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                stops.map { Feature.fromGeometry(it.position.toPoint()) }
            )
        )
    }
    LaunchedEffect(style, userLocation) {
        style?.getSourceAs<GeoJsonSource>(USER_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(userLocation?.let { Feature.fromGeometry(it.toPoint()) })
            )
        )
    }
    LaunchedEffect(center) {
        if (center == null) return@LaunchedEffect
        mapView.getMapAsync { map ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    org.maplibre.android.geometry.LatLng(center.latitude, center.longitude),
                    FOLLOW_ZOOM
                )
            )
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

private fun addMarkerLayers(style: Style) {
    style.addSource(GeoJsonSource(STOPS_SOURCE))
    style.addSource(GeoJsonSource(USER_SOURCE))
    style.addLayer(
        CircleLayer("stops-layer", STOPS_SOURCE).withProperties(
            circleRadius(6f),
            circleColor("#0B5394"),
            circleStrokeColor("#FFFFFF"),
            circleStrokeWidth(2f)
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
