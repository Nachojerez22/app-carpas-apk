package com.nachojerez.carpstrategy.ui.guided

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.advice.WindRules
import com.nachojerez.carpstrategy.domain.advice.WindTemperature
import com.nachojerez.carpstrategy.domain.guided.MapWind
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotMap
import com.nachojerez.carpstrategy.domain.guided.SpotWindKind
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** Qué está haciendo el usuario en el mapa. */
private sealed interface MapMode {
    data object Browse : MapMode

    /** Primer toque: dónde está el puesto. [spot] = puesto existente que se reubica. */
    data class PickSpot(val spot: Spot?) : MapMode

    /** Segundo toque: la orilla de enfrente, hacia donde lanzas. */
    data class PickOpposite(val spot: Spot?, val point: GeoPoint) : MapMode
}

/**
 * Mapa de puestos (fase 7.9): satélite del embalse con tus puestos coloreados según cómo les da el
 * viento (§5.4) y su línea de lance. Para crear o reubicar un puesto se toca el puesto y la orilla
 * de enfrente: la app calcula hacia dónde mira y el agua abierta que tiene delante. El tiempo es el
 * mismo para todo el embalse: lo que cambia es cómo le da el viento a cada puesto.
 */
@Composable
fun SpotsMapScreen(onBack: () -> Unit, viewModel: SpotsViewModel = hiltViewModel()) {
    val spots by viewModel.spots.collectAsStateWithLifecycle()
    val center by viewModel.center.collectAsStateWithLifecycle()
    val wind by viewModel.wind.collectAsStateWithLifecycle()
    val reservoirName by viewModel.reservoirName.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf<MapMode>(MapMode.Browse) }
    var draft by remember { mutableStateOf<SpotDraft?>(null) }
    var tooShort by remember { mutableStateOf(false) }
    // Textos resueltos fuera del mapa: dentro solo van composables del mapa.
    val res = LocalContext.current.resources
    val newSpotTitle = stringResource(R.string.spots_map_new_spot)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            Text(reservoirName ?: stringResource(R.string.spots_map_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        val here = center
        if (!BuildConfig.MAPS_CONFIGURED || here == null) {
            CarpCard(modifier = Modifier.padding(Spacing.screen)) {
                Caption(stringResource(if (!BuildConfig.MAPS_CONFIGURED) R.string.spots_map_no_key else R.string.spots_map_no_place))
            }
            return@Column
        }
        Box(Modifier.weight(1f)) {
            val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(here.toLatLng(), MAP_ZOOM) }
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = camera,
                properties = MapProperties(mapType = MapType.HYBRID),
                uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
                onMapClick = { latLng ->
                    val point = GeoPoint(latLng.latitude, latLng.longitude)
                    when (val m = mode) {
                        is MapMode.PickSpot -> {
                            tooShort = false
                            mode = MapMode.PickOpposite(m.spot, point)
                        }
                        is MapMode.PickOpposite -> {
                            val geometry = SpotMap.geometry(m.point, point)
                            if (geometry == null) {
                                tooShort = true
                            } else {
                                val base = m.spot?.let { SpotDraft.of(it) } ?: SpotDraft()
                                draft = base.copy(point = m.point, facingDeg = geometry.facingDeg, fetch = geometry.fetchM.toLong().toString())
                                mode = MapMode.Browse
                            }
                        }
                        MapMode.Browse -> Unit
                    }
                },
            ) {
                spots.forEach { spot ->
                    val point = spot.point ?: return@forEach
                    key(spot.id) {
                        val kind = SpotMap.windKind(spot, wind)
                        Marker(
                            state = rememberUpdatedMarkerState(position = point.toLatLng()),
                            title = spot.name,
                            snippet = res.getString(kind.titleRes()),
                            icon = BitmapDescriptorFactory.defaultMarker(kind.hue()),
                            onInfoWindowClick = { draft = SpotDraft.of(spot) },
                        )
                        val facing = spot.facingDeg
                        val fetch = spot.fetchM
                        if (facing != null && fetch != null) {
                            val end = SpotMap.destination(point, facing.toDouble(), fetch)
                            Polyline(points = listOf(point.toLatLng(), end.toLatLng()), color = kind.color(), width = LINE_WIDTH)
                        }
                    }
                }
                when (val m = mode) {
                    is MapMode.PickOpposite -> Marker(
                        state = rememberUpdatedMarkerState(position = m.point.toLatLng()),
                        title = newSpotTitle,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW),
                    )
                    else -> Unit
                }
            }
            WindCard(wind, Modifier.align(Alignment.TopCenter).padding(Spacing.sm))
        }
        Column(Modifier.fillMaxWidth().padding(Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            when (val m = mode) {
                MapMode.Browse -> {
                    Caption(stringResource(R.string.spots_map_hint))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = { mode = MapMode.PickSpot(null) }) { Text(stringResource(R.string.spots_map_add)) }
                        val unplaced = spots.firstOrNull { it.point == null }
                        if (unplaced != null) {
                            OutlinedButton(onClick = { mode = MapMode.PickSpot(unplaced) }) { Text(stringResource(R.string.spots_map_place, unplaced.name)) }
                        }
                    }
                }
                is MapMode.PickSpot -> {
                    Text(stringResource(R.string.spots_map_step_spot), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { mode = MapMode.Browse }) { Text(stringResource(R.string.action_cancel)) }
                }
                is MapMode.PickOpposite -> {
                    Text(stringResource(R.string.spots_map_step_opposite), style = MaterialTheme.typography.titleSmall)
                    if (tooShort) Caption(stringResource(R.string.spots_map_too_short), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { mode = MapMode.Browse; tooShort = false }) { Text(stringResource(R.string.action_cancel)) }
                }
            }
            Caption(stringResource(R.string.spots_map_legend))
        }
    }

    draft?.let { current ->
        SpotDialog(
            draft = current,
            onChange = { draft = it },
            onSave = {
                viewModel.save(current)
                draft = null
            },
            onDelete = current.id?.let { id -> { viewModel.delete(id); draft = null } },
            onDismiss = { draft = null },
            onRelocate = current.id?.let { _ ->
                {
                    val spot = spots.firstOrNull { it.id == current.id }
                    draft = null
                    mode = MapMode.PickSpot(spot)
                }
            },
        )
    }
}

/** El viento sobre el embalse: el mismo para todos los puestos (la previsión no distingue orillas). */
@Composable
private fun WindCard(wind: MapWind?, modifier: Modifier = Modifier) {
    val from = wind?.fromDeg
    val kmh = wind?.kmh
    val text = if (from == null || kmh == null) {
        stringResource(R.string.spots_map_wind_unknown)
    } else {
        val temp = when (WindRules.temperature(wind.airC, wind.waterC)) {
            WindTemperature.WARM -> stringResource(R.string.spots_map_wind_warm)
            WindTemperature.COLD -> stringResource(R.string.spots_map_wind_cold)
            else -> ""
        }
        stringResource(R.string.spots_map_wind, Formatting.compass(from), Formatting.number(kmh, 0), Formatting.compass(from + 180)) + temp
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = modifier.background(Color(0xCC000000), RoundedCornerShape(Spacing.sm)).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private fun SpotWindKind.hue(): Float = when (this) {
    SpotWindKind.FAVOURED -> BitmapDescriptorFactory.HUE_GREEN
    SpotWindKind.RECEIVES -> BitmapDescriptorFactory.HUE_AZURE
    SpotWindKind.SHELTERED -> BitmapDescriptorFactory.HUE_VIOLET
    SpotWindKind.SIDE -> BitmapDescriptorFactory.HUE_ORANGE
    SpotWindKind.NEUTRAL -> BitmapDescriptorFactory.HUE_ROSE
}

private fun SpotWindKind.color(): Color = when (this) {
    SpotWindKind.FAVOURED -> Color(0xFF2E7D32)
    SpotWindKind.RECEIVES -> Color(0xFF0288D1)
    SpotWindKind.SHELTERED -> Color(0xFF7B1FA2)
    SpotWindKind.SIDE -> Color(0xFFEF6C00)
    SpotWindKind.NEUTRAL -> Color(0xFFE0E0E0)
}

private fun SpotWindKind.titleRes(): Int = when (this) {
    SpotWindKind.FAVOURED -> R.string.spot_wind_favoured
    SpotWindKind.RECEIVES -> R.string.spot_wind_receives
    SpotWindKind.SHELTERED -> R.string.spot_wind_sheltered
    SpotWindKind.SIDE -> R.string.spot_wind_side
    SpotWindKind.NEUTRAL -> R.string.spot_wind_neutral
}

/** Zoom inicial: un embalse de 1–2 km entero en pantalla. */
private const val MAP_ZOOM = 15f
private const val LINE_WIDTH = 6f
