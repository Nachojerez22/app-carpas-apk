package com.nachojerez.carpstrategy.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.DataCard
import com.nachojerez.carpstrategy.ui.components.EvidenceBadge
import com.nachojerez.carpstrategy.ui.components.LegalWindowBar
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.components.SourcePriorityRow
import com.nachojerez.carpstrategy.ui.components.TrendChart
import com.nachojerez.carpstrategy.ui.components.WarningChip
import com.nachojerez.carpstrategy.ui.components.WarningType
import com.nachojerez.carpstrategy.ui.components.WaterTempCard
import com.nachojerez.carpstrategy.ui.components.toKind
import com.nachojerez.carpstrategy.ui.conditions.ConditionsUiState
import com.nachojerez.carpstrategy.ui.conditions.ConditionsViewModel
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.conditions.Formatting.number
import com.nachojerez.carpstrategy.ui.conditions.Formatting.signed
import com.nachojerez.carpstrategy.ui.conditions.PriorityActions
import com.nachojerez.carpstrategy.ui.manual.labelRes
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.SourceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Duration
import java.time.format.DateTimeFormatter

private val DAY_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Formatting.SPANISH)

@Composable
fun TodayScreen(
    onOpenRawData: () -> Unit,
    onAddMeasurement: () -> Unit,
    onOpenPlace: () -> Unit,
    viewModel: ConditionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TodayContent(
        state = state,
        onRefresh = viewModel::refresh,
        onOpenRawData = onOpenRawData,
        onAddMeasurement = onAddMeasurement,
        onOpenPlace = onOpenPlace,
        priorityActions = PriorityActions(viewModel::movePriorityUp, viewModel::movePriorityDown, viewModel::togglePriority),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayContent(
    state: ConditionsUiState,
    onRefresh: () -> Unit,
    onOpenRawData: () -> Unit,
    onAddMeasurement: () -> Unit,
    onOpenPlace: () -> Unit,
    priorityActions: PriorityActions,
) {
    val raw = state.raw
    val derived = state.derived
    val overline = "${DAY_TITLE.format(state.now.atZone(Formatting.MADRID))} · ${state.locationName}"
    val pressureChart = remember(raw, state.now.epochSecond / 3600) {
        raw?.let { TodayCharts.pressure(it.merged, it.ensemble, state.now) }
    }
    val temperatureChart = remember(raw, state.now.epochSecond / 3600) {
        raw?.let { TodayCharts.temperature(it.merged, it.ensemble, state.now) }
    }
    val stale = state.forecastFreshness?.level == FreshnessLevel.STALE
    val offline = state.forecastError == DataError.NETWORK

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScreenHeader(stringResource(R.string.today_title), overline) }

        if (state.isLoading || state.isRefreshing) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Caption(stringResource(R.string.today_loading))
                }
            }
        }

        // Vacío: sin ningún dato todavía.
        if (!state.isLoading && derived == null && raw?.forecast == null && raw?.observations == null) {
            item {
                CarpCard {
                    Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.today_empty_text))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Button(onClick = onOpenPlace) { Text(stringResource(R.string.today_set_location)) }
                        OutlinedButton(onClick = onAddMeasurement) { Text(stringResource(R.string.water_add_measurement)) }
                    }
                }
            }
        }

        // Antigüedad de los datos y actualizar.
        item { FreshnessBar(state, offline, onRefresh) }

        state.forecastError?.takeIf { it != DataError.NETWORK }?.let { error ->
            item {
                CarpCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Text(stringResource(R.string.today_error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(errorText(error), color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }

        // Avisos.
        val unverified = raw?.manualRecords.orEmpty().count { it.origin is ManualOrigin.Imported }
        if (state.divergentForecastHours > 0 || unverified > 0 || stale) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    if (stale) WarningChip(WarningType.Stale, stringResource(R.string.warning_stale), painterResource(R.drawable.ic_clock))
                    if (state.divergentForecastHours > 0) {
                        WarningChip(WarningType.ModelDivergence, stringResource(R.string.warning_divergence), painterResource(R.drawable.ic_warning))
                    }
                    if (unverified > 0) {
                        WarningChip(WarningType.Unverified, stringResource(R.string.warning_unverified, unverified), painterResource(R.drawable.ic_nav_about))
                    }
                }
            }
        }

        if (derived != null || raw?.forecast != null) {
            item { WaterTempCard(derived?.water, onAddMeasurement) }
            item { DataGrid(state, stale || offline) }
            derived?.let { d ->
                item {
                    CarpCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.legal_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            val inside = d.legalToday?.let { state.now in it } == true
                            Text(
                                stringResource(if (inside) R.string.legal_now_inside else R.string.legal_now_outside),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (inside) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            )
                        }
                        d.legalToday?.let {
                            Text("${Formatting.clock(it.start)} – ${Formatting.clock(it.end)}", style = MaterialTheme.typography.headlineSmall)
                        }
                        LegalWindowBar(d.legalToday, d.sunToday, state.now)
                        Caption(stringResource(R.string.legal_caption, Formatting.clock(d.sunToday.sunrise), Formatting.clock(d.sunToday.sunset)))
                    }
                }
                item {
                    CarpCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.weight_zero_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            EvidenceBadge(EvidenceKind.NoEvidence, short = true)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                            Column(Modifier.weight(1f)) {
                                Text("${number(d.moon.illumination * 100, 0)} %", style = MaterialTheme.typography.titleLarge)
                                Caption(stringResource(R.string.weight_zero_moon))
                            }
                            Column(Modifier.weight(1f)) {
                                val p = raw?.merged?.lastOrNull { !it.time.isAfter(state.now) }?.get(WeatherVariable.PRESSURE_MSL)?.value
                                Text("${number(p, 0)} hPa ${trendArrow(d.pressure.delta3hHpa)}", style = MaterialTheme.typography.titleLarge)
                                Caption(stringResource(R.string.weight_zero_pressure))
                            }
                        }
                    }
                }
            }
            pressureChart?.takeIf { !it.isEmpty }?.let { chart ->
                item {
                    CarpCard {
                        Text(stringResource(R.string.chart_pressure_title), style = MaterialTheme.typography.titleMedium)
                        Caption(stringResource(R.string.chart_pressure_subtitle))
                        TrendChart(chart, stringResource(R.string.chart_pressure_title))
                        Caption(stringResource(R.string.chart_legend))
                    }
                }
            }
            temperatureChart?.takeIf { !it.isEmpty }?.let { chart ->
                item {
                    CarpCard {
                        Text(stringResource(R.string.chart_temperature_title), style = MaterialTheme.typography.titleMedium)
                        Caption(stringResource(R.string.chart_temperature_subtitle))
                        TrendChart(chart, stringResource(R.string.chart_temperature_title), warmBand = true)
                        Caption(stringResource(R.string.chart_temperature_legend))
                    }
                }
            }
        }

        raw?.let { r ->
            item { SectionTitle(stringResource(R.string.priority_title), subtitle = stringResource(R.string.priority_explanation_short)) }
            item {
                CarpCard {
                    val disabled = DataSource.entries.filterNot(r.priority::isEnabled)
                    (r.priority.order + disabled).forEachIndexed { index, source ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val position = r.priority.order.indexOf(source)
                        SourcePriorityRow(
                            position = if (position >= 0) position + 1 else null,
                            source = source.toKind(),
                            name = stringResource(source.labelRes()),
                            description = stringResource(source.descriptionRes()),
                            canMoveUp = position > 0,
                            canMoveDown = position in 0 until r.priority.order.lastIndex,
                            canToggle = position < 0 || r.priority.order.size > 1,
                            onMoveUp = { priorityActions.moveUp(source) },
                            onMoveDown = { priorityActions.moveDown(source) },
                            onToggle = { priorityActions.toggle(source) },
                        )
                    }
                }
            }
        }
        item {
            TextButton(onClick = onOpenRawData) { Text(stringResource(R.string.today_open_raw)) }
        }
    }
}

@Composable
private fun FreshnessBar(state: ConditionsUiState, offline: Boolean, onRefresh: () -> Unit) {
    val freshness = state.forecastFreshness
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (offline) Icon(painterResource(R.drawable.ic_cloud_off), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    freshness == null -> stringResource(R.string.today_no_download)
                    offline -> stringResource(R.string.today_offline, ageText(freshness.age))
                    else -> stringResource(R.string.today_age, ageText(freshness.age))
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Caption(stringResource(R.string.today_sources_line))
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.isRefreshing) {
            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null)
            Text(stringResource(R.string.action_refresh))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataGrid(state: ConditionsUiState, oldData: Boolean) {
    val raw = state.raw
    val d = state.derived
    val now = state.now
    val last = raw?.merged?.lastOrNull { !it.time.isAfter(now) }
    val air = last?.get(WeatherVariable.AIR_TEMPERATURE)
    val wind = d?.wind24h
    val windSource = last?.get(WeatherVariable.WIND_SPEED)?.source
    // Rango de viento entre modelos en las próximas 24 h, si discrepan.
    val windRange = raw?.ensemble
        ?.filter { !it.time.isBefore(now) && it.time.isBefore(now.plus(Duration.ofHours(24))) && it.windDivergent }
        ?.mapNotNull { it.windSpeedKmh }
        ?.takeIf { it.isNotEmpty() }
        ?.let { stats -> stringResource(R.string.data_wind_models, number(stats.minOf { it.min }, 0), number(stats.maxOf { it.max }, 0)) }
    val cards = listOf<@Composable (Modifier) -> Unit>(
        { m ->
            DataCard(
                stringResource(R.string.data_air), number(air?.value, 0), "°C", air?.source?.toKind(),
                d?.airTrend?.let { stringResource(R.string.data_air_trend, signed(it.deltaC)) }, m,
            )
        },
        { m ->
            DataCard(
                stringResource(R.string.data_wind),
                "${Formatting.compass(wind?.dominantDirectionDeg)} ${number(wind?.meanSpeedKmh, 0)}",
                "km/h",
                windSource?.toKind(),
                wind?.let { stringResource(R.string.data_wind_persistence, number(it.persistence * 100, 0), number(it.maxGustKmh, 0)) },
                m,
                warning = windRange,
            )
        },
        { m ->
            DataCard(
                stringResource(R.string.data_rain), number(d?.rain?.last24hMm), stringResource(R.string.data_rain_unit),
                last?.get(WeatherVariable.PRECIPITATION)?.source?.toKind(),
                d?.rain?.let { stringResource(R.string.data_rain_72h, number(it.last72hMm)) }, m,
            )
        },
        { m ->
            val level = d?.reservoir
            DataCard(
                stringResource(R.string.data_reservoir), number(level?.percent, 0), "%",
                level?.let { SourceKind.Manual },
                level?.let { l ->
                    l.delta7dHm3?.let { stringResource(R.string.data_reservoir_delta, signed(it, 2)) } ?: stringResource(R.string.derived_reservoir_no_delta)
                } ?: stringResource(R.string.data_reservoir_missing),
                m,
                secondary = level?.volumeHm3?.let { "${number(it, 2)} hm³" },
            )
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        cards.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { card -> card(Modifier.weight(1f)) }
            }
        }
        if (oldData) Caption(stringResource(R.string.today_old_data_note))
    }
}

private fun trendArrow(delta: Double?): String = when {
    delta == null -> ""
    delta > 0.5 -> "↑"
    delta < -0.5 -> "↓"
    else -> "→"
}

@Composable
private fun ageText(age: Duration): String {
    val (unit, value) = Formatting.ageParts(age)
    return when (unit) {
        Formatting.AgeUnit.NOW -> stringResource(R.string.age_now)
        Formatting.AgeUnit.MINUTES -> stringResource(R.string.age_minutes, value.toInt())
        Formatting.AgeUnit.HOURS -> stringResource(R.string.age_hours, value.toInt())
        Formatting.AgeUnit.DAYS -> stringResource(R.string.age_days, value.toInt())
    }
}

@Composable
private fun errorText(error: DataError): String = stringResource(
    when (error) {
        DataError.NETWORK -> R.string.error_network
        DataError.SERVER -> R.string.error_server
        DataError.INVALID_RESPONSE -> R.string.error_invalid_response
        DataError.MISSING_API_KEY -> R.string.error_missing_api_key
        DataError.UNAUTHORIZED -> R.string.error_unauthorized
        DataError.RATE_LIMITED -> R.string.error_rate_limited
        DataError.NO_DATA -> R.string.error_no_data
    },
)

private fun DataSource.descriptionRes(): Int = when (this) {
    DataSource.MANUAL -> R.string.source_manual_desc
    DataSource.AEMET -> R.string.source_aemet_desc
    DataSource.MODELS -> R.string.source_models_desc
}
