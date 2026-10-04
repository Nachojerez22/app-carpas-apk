package com.nachojerez.carpstrategy.ui.place

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.model.AppStyle
import com.nachojerez.carpstrategy.domain.model.AppThemeMode
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.ui.account.AccountCard
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.EvidenceBadge
import com.nachojerez.carpstrategy.ui.components.RegulationBadge
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.components.SourceBadge
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing

/** Pestaña Lugar: ubicación, apariencia, fuentes, leyenda, limitaciones y normativa. */
@Composable
fun PlaceScreen(onOpenSpots: () -> Unit = {}, onOpenAssistant: () -> Unit = {}, viewModel: PlaceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val myLocationName = stringResource(R.string.place_my_location)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.useDeviceLocation(myLocationName) else viewModel.onPermissionDenied()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScreenHeader(stringResource(R.string.place_title), state.location.name) }

        item { SectionTitle(stringResource(R.string.account_title), subtitle = stringResource(R.string.account_subtitle)) }
        item { AccountCard() }

        item { SectionTitle(stringResource(R.string.place_location_title), subtitle = stringResource(R.string.place_location_subtitle)) }
        item {
            LocationCard(
                state = state,
                onUseGps = {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onSave = viewModel::saveCoordinates,
                onReset = viewModel::resetToDefault,
            )
        }

        item { SectionTitle(stringResource(R.string.spots_title), subtitle = stringResource(R.string.spots_open_detail)) }
        item {
            CarpCard {
                Caption(stringResource(R.string.spots_intro))
                OutlinedButton(onClick = onOpenSpots) { Text(stringResource(R.string.spots_open)) }
            }
        }

        item { SectionTitle(stringResource(R.string.ai_settings_title), subtitle = stringResource(R.string.ai_settings_subtitle)) }
        item {
            CarpCard {
                OutlinedButton(onClick = onOpenAssistant) { Text(stringResource(R.string.ai_settings_open)) }
            }
        }

        item { SectionTitle(stringResource(R.string.place_appearance_title)) }
        item {
            AppearanceCard(
                state = state,
                onStyle = viewModel::setStyle,
                onMode = viewModel::setMode,
                onDynamic = viewModel::setDynamicColor,
            )
        }

        item { SectionTitle(stringResource(R.string.place_sources_title)) }
        item {
            CarpCard {
                SourceRow(SourceKind.Forecast, stringResource(R.string.place_source_openmeteo), stringResource(R.string.about_source_openmeteo))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SourceRow(SourceKind.Aemet, stringResource(R.string.source_aemet), stringResource(R.string.about_source_aemet))
                Caption(
                    stringResource(if (BuildConfig.AEMET_API_KEY.isBlank()) R.string.about_aemet_key_missing else R.string.about_aemet_key_present),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SourceRow(SourceKind.Manual, stringResource(R.string.source_manual), stringResource(R.string.place_source_manual))
            }
        }

        item { SectionTitle(stringResource(R.string.place_legend_title), subtitle = stringResource(R.string.place_legend_subtitle)) }
        item {
            CarpCard {
                LegendRow({ EvidenceBadge(EvidenceKind.Strong) }, stringResource(R.string.legend_strong))
                LegendRow({ EvidenceBadge(EvidenceKind.Moderate) }, stringResource(R.string.legend_moderate))
                LegendRow({ EvidenceBadge(EvidenceKind.NoEvidence) }, stringResource(R.string.legend_none))
                LegendRow({ EvidenceBadge(EvidenceKind.LocalHypothesis) }, stringResource(R.string.legend_local))
                LegendRow({ EvidenceBadge(EvidenceKind.IndividualVariability) }, stringResource(R.string.legend_individual))
                LegendRow({ RegulationBadge() }, stringResource(R.string.legend_regulation))
            }
        }

        item { SectionTitle(stringResource(R.string.about_disclaimers_title)) }
        item {
            CarpCard {
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_disclaimer_water))
                Text(stringResource(R.string.about_disclaimer_rules))
                Text(stringResource(R.string.about_disclaimer_legal))
            }
        }

        item { SectionTitle(stringResource(R.string.place_regulation_title)) }
        item {
            CarpCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RegulationBadge()
                    Text(stringResource(R.string.place_regulation_hours), modifier = Modifier.weight(1f))
                }
                Text(
                    stringResource(R.string.place_regulation_reviewed, state.regulationReviewed ?: Formatting.number(null)),
                    style = MaterialTheme.typography.labelLarge,
                )
                Caption(stringResource(R.string.place_regulation_check))
            }
        }

        item {
            Caption(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
        }
    }
}

@Composable
private fun LocationCard(
    state: PlaceUiState,
    onUseGps: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onReset: () -> Unit,
) {
    val point = state.location.point
    var name by rememberSaveable(state.location) { mutableStateOf(state.location.name) }
    var lat by rememberSaveable(state.location) { mutableStateOf(Formatting.number(point.latitude, 5)) }
    var lon by rememberSaveable(state.location) { mutableStateOf(Formatting.number(point.longitude, 5)) }

    CarpCard {
        Text(state.location.name, style = MaterialTheme.typography.titleMedium)
        Caption("${Formatting.number(point.latitude, 4)}, ${Formatting.number(point.longitude, 4)}")
        Button(onClick = onUseGps, enabled = state.gps != GpsStatus.LOCATING, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null)
            Text(stringResource(R.string.place_use_gps))
        }
        when (state.gps) {
            GpsStatus.LOCATING -> LinearProgressIndicator(Modifier.fillMaxWidth())
            GpsStatus.FOUND -> Caption(stringResource(R.string.place_gps_found))
            GpsStatus.PERMISSION_DENIED -> Caption(stringResource(R.string.place_gps_denied), color = MaterialTheme.colorScheme.error)
            GpsStatus.UNAVAILABLE -> Caption(stringResource(R.string.place_gps_unavailable), color = MaterialTheme.colorScheme.error)
            GpsStatus.IDLE -> Unit
        }
        Caption(stringResource(R.string.place_gps_privacy))

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.place_coordinates_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.place_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = lat,
                onValueChange = { lat = it },
                label = { Text(stringResource(R.string.place_latitude)) },
                singleLine = true,
                isError = state.coordinateError == CoordinateError.LATITUDE,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = lon,
                onValueChange = { lon = it },
                label = { Text(stringResource(R.string.place_longitude)) },
                singleLine = true,
                isError = state.coordinateError == CoordinateError.LONGITUDE,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f),
            )
        }
        state.coordinateError?.let {
            Caption(
                stringResource(if (it == CoordinateError.LATITUDE) R.string.place_latitude_error else R.string.place_longitude_error),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { onSave(lat, lon, name) }) { Text(stringResource(R.string.place_save_coordinates)) }
            if (state.location != DefaultLocation.value) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.place_reset, DefaultLocation.value.name)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceCard(
    state: PlaceUiState,
    onStyle: (AppStyle) -> Unit,
    onMode: (AppThemeMode) -> Unit,
    onDynamic: (Boolean) -> Unit,
) {
    val appearance = state.appearance
    CarpCard {
        Text(stringResource(R.string.appearance_style), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val styles = listOf(AppStyle.MATERIAL to R.string.appearance_style_material, AppStyle.APPLE to R.string.appearance_style_apple)
            styles.forEachIndexed { i, (style, label) ->
                SegmentedButton(
                    selected = appearance.style == style,
                    onClick = { onStyle(style) },
                    shape = SegmentedButtonDefaults.itemShape(i, styles.size),
                ) { Text(stringResource(label)) }
            }
        }
        Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(
                AppThemeMode.SYSTEM to R.string.appearance_theme_system,
                AppThemeMode.LIGHT to R.string.appearance_theme_light,
                AppThemeMode.DARK to R.string.appearance_theme_dark,
            )
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = appearance.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(stringResource(label)) }
            }
        }
        if (appearance.style == AppStyle.MATERIAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.appearance_dynamic), style = MaterialTheme.typography.titleSmall)
                    Caption(stringResource(R.string.appearance_dynamic_hint))
                }
                Switch(checked = appearance.dynamicColor, onCheckedChange = onDynamic)
            }
        }
        Caption(stringResource(R.string.appearance_fixed_colors))
    }
}

@Composable
private fun SourceRow(source: SourceKind, name: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SourceBadge(source, size = 24)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Caption(text)
        }
    }
}

@Composable
private fun LegendRow(badge: @Composable () -> Unit, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        badge()
        Caption(text)
    }
}
