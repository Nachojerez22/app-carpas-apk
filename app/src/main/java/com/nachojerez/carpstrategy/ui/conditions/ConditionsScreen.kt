package com.nachojerez.carpstrategy.ui.conditions

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.EnsembleHour
import com.nachojerez.carpstrategy.domain.derived.Freshness
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.HourlyWeather
import com.nachojerez.carpstrategy.domain.model.StationObservation
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.ui.conditions.Formatting.number

/** Qué serie se muestra en la tabla horaria. */
private enum class SeriesView(@StringRes val label: Int, val model: WeatherModel?) {
    Mean(R.string.model_mean, null),
    IconEu(R.string.model_icon_eu, WeatherModel.ICON_EU),
    Arpege(R.string.model_arpege, WeatherModel.ARPEGE_EUROPE),
    Ecmwf(R.string.model_ecmwf, WeatherModel.ECMWF_IFS025),
}

@Composable
fun ConditionsScreen(viewModel: ConditionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConditionsContent(state = state, onRefresh = viewModel::refresh)
}

@Composable
fun ConditionsContent(state: ConditionsUiState, onRefresh: () -> Unit) {
    var view by rememberSaveable { mutableStateOf(SeriesView.Mean) }
    val raw = state.raw
    val forecast = raw?.forecast?.data
    val ensemble = raw?.ensemble.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        item { Header(state, onRefresh) }

        if (state.isLoading) {
            item { Text(stringResource(R.string.raw_loading)) }
            return@LazyColumn
        }
        if (raw?.forecast == null && raw?.observations == null) {
            item { Text(stringResource(R.string.raw_no_data)) }
        }

        item { ObservationsCard(state) }

        item { ForecastCard(state) }

        if (forecast != null) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SeriesView.entries.forEach { option ->
                        FilterChip(
                            selected = view == option,
                            onClick = { view = option },
                            label = { Text(stringResource(option.label)) },
                        )
                    }
                }
            }
            val model = view.model
            if (model == null) {
                item { Caption(stringResource(R.string.mean_explanation)) }
                ensembleRows(ensemble)
            } else {
                val hours = forecast.series[model]
                if (hours.isNullOrEmpty()) {
                    item { Text(stringResource(R.string.model_missing)) }
                } else {
                    modelRows(hours)
                }
            }
        }
    }
}

@Composable
private fun Header(state: ConditionsUiState, onRefresh: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.raw_title), style = MaterialTheme.typography.headlineSmall)
            val requested = state.raw?.forecast?.data?.requested
            if (requested != null) {
                Caption(stringResource(R.string.raw_subtitle, state.locationName, requested.latitude, requested.longitude))
            } else {
                Caption(state.locationName)
            }
        }
        if (state.isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else {
            Button(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
        }
    }
}

@Composable
private fun ForecastCard(state: ConditionsUiState) {
    val cached = state.raw?.forecast
    SectionCard(title = stringResource(R.string.openmeteo_title)) {
        state.forecastFreshness?.let { FreshnessText(it) }
        state.forecastError?.let { ErrorText(it) }
        if (cached != null) {
            val f = cached.data
            Caption(stringResource(R.string.openmeteo_grid, f.gridPoint.latitude, f.gridPoint.longitude, number(f.elevationM, 0)))
            if (state.divergentForecastHours > 0) {
                Text(
                    stringResource(R.string.wind_divergence_warning, state.divergentForecastHours),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(stringResource(R.string.wind_divergence_none))
            }
        }
        Caption(stringResource(R.string.openmeteo_attribution))
    }
}

@Composable
private fun ObservationsCard(state: ConditionsUiState) {
    val cached = state.raw?.observations
    SectionCard(title = stringResource(R.string.aemet_title)) {
        state.observationsFreshness?.let { FreshnessText(it) }
        state.observationsError?.let { ErrorText(it) }
        if (cached == null) {
            if (state.observationsError == null) Text(stringResource(R.string.aemet_no_observations))
        } else {
            val data = cached.data
            Text(
                stringResource(
                    R.string.aemet_station,
                    data.station.name,
                    data.station.id,
                    number(data.distanceKm),
                ),
            )
            data.observations.asReversed().forEach { ObservationRow(it) }
        }
        Caption(stringResource(R.string.aemet_attribution))
    }
}

private fun LazyListScope.ensembleRows(hours: List<EnsembleHour>) {
    items(hours, key = { it.time.epochSecond }) { h ->
        Column {
            Text(
                stringResource(
                    R.string.row_forecast,
                    Formatting.hour(h.time),
                    Formatting.withSpread(h.temperatureC),
                    Formatting.withSpread(h.pressureMslHpa),
                    Formatting.withSpread(h.windSpeedKmh),
                    Formatting.direction(h.windDirection),
                    Formatting.withSpread(h.windGustsKmh),
                    Formatting.withSpread(h.cloudCoverPct, 0),
                    Formatting.withSpread(h.precipitationMm),
                    Formatting.withSpread(h.shortwaveRadiationWm2, 0),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (h.windDivergent) {
                Text(
                    stringResource(R.string.row_divergent),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun LazyListScope.modelRows(hours: List<HourlyWeather>) {
    items(hours, key = { it.time.epochSecond }) { h ->
        Column {
            Text(
                stringResource(
                    R.string.row_forecast,
                    Formatting.hour(h.time),
                    number(h.temperatureC),
                    number(h.pressureMslHpa),
                    number(h.windSpeedKmh),
                    Formatting.compass(h.windDirectionDeg),
                    number(h.windGustsKmh),
                    number(h.cloudCoverPct, 0),
                    number(h.precipitationMm),
                    number(h.shortwaveRadiationWm2, 0),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ObservationRow(o: StationObservation) {
    Text(
        stringResource(
            R.string.row_observation,
            Formatting.hour(o.time),
            number(o.temperatureC),
            number(o.pressureMslHpa),
            number(o.windSpeedKmh),
            Formatting.compass(o.windDirectionDeg),
            number(o.windGustKmh),
            number(o.precipitationMm),
            number(o.relativeHumidityPct, 0),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun FreshnessText(freshness: Freshness) {
    val (unit, value) = Formatting.ageParts(freshness.age)
    val age = when (unit) {
        Formatting.AgeUnit.NOW -> stringResource(R.string.age_now)
        Formatting.AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value.toInt())
        Formatting.AgeUnit.HOURS -> stringResource(R.string.age_hours, value.toInt())
        Formatting.AgeUnit.DAYS -> stringResource(R.string.age_days, value.toInt())
    }
    when (freshness.level) {
        FreshnessLevel.FRESH -> Caption(stringResource(R.string.freshness_fresh, age))
        FreshnessLevel.AGING -> Text(stringResource(R.string.freshness_aging, age))
        FreshnessLevel.STALE -> Text(stringResource(R.string.freshness_stale, age), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ErrorText(error: DataError) {
    val message = when (error) {
        DataError.NETWORK -> R.string.error_network
        DataError.SERVER -> R.string.error_server
        DataError.INVALID_RESPONSE -> R.string.error_invalid_response
        DataError.MISSING_API_KEY -> R.string.error_missing_api_key
        DataError.UNAUTHORIZED -> R.string.error_unauthorized
        DataError.RATE_LIMITED -> R.string.error_rate_limited
        DataError.NO_DATA -> R.string.error_no_data
    }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
