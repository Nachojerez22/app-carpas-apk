package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.domain.advice.PlanSource
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Solo lee la última preparación guardada: abrirla no gasta red ni IA. */
@HiltViewModel
class PreparedOutingViewModel @Inject constructor(
    localSettings: LocalSettings,
    private val clock: Clock,
    private val activation: PreparedActivation,
) : ViewModel() {
    val outing: StateFlow<PreparedOuting?> = localSettings.observe(KEY_PREPARED_OUTING).map(PreparedOutingJson::decode)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Fase 7.21: se puede activar si es la de hoy y de este embalse. */
    val canActivate: StateFlow<Boolean> = activation.observeToday().map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val activatingFlow = MutableStateFlow(false)
    val activating: StateFlow<Boolean> = activatingFlow

    fun activate(onStarted: () -> Unit) {
        val prepared = outing.value ?: return
        if (activatingFlow.value || !canActivate.value) return
        activatingFlow.value = true
        viewModelScope.launch {
            try {
                activation.activate(prepared)
                onStarted()
            } finally {
                activatingFlow.value = false
            }
        }
    }

    fun now(): Instant = clock.instant()
}

/**
 * «Próxima salida» (fase 7.19): lo que salió al pulsar «Preparar la próxima salida», para
 * repasarlo con calma (condiciones a esa hora, plan por caña, engodo y qué hacer si cambia algo).
 * Es una foto de cuando se preparó; para actualizarla, se vuelve a preparar desde Estrategia.
 */
@Composable
fun PreparedOutingScreen(onBack: () -> Unit, onStartGuided: () -> Unit = {}, viewModel: PreparedOutingViewModel = hiltViewModel()) {
    val outing by viewModel.outing.collectAsStateWithLifecycle()
    val canActivate by viewModel.canActivate.collectAsStateWithLifecycle()
    val activating by viewModel.activating.collectAsStateWithLifecycle()
    PreparedOutingContent(outing, viewModel.now(), onBack, canActivate, activating) { viewModel.activate(onStartGuided) }
}

@Composable
fun PreparedOutingContent(
    outing: PreparedOuting?,
    now: Instant,
    onBack: () -> Unit = {},
    canActivate: Boolean = false,
    activating: Boolean = false,
    onActivate: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
            ScreenHeader(stringResource(R.string.prepared_title), stringResource(R.string.prepared_overline))
        }
        if (outing == null) {
            item { CarpCard { Text(stringResource(R.string.prepared_empty)) } }
            return@LazyColumn
        }
        item { SummaryCard(outing, now) }
        // Fase 7.21: ya en el puesto, empezar con esto en un toque.
        if (canActivate) {
            item {
                Button(onClick = onActivate, enabled = !activating, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (activating) R.string.guided_starting else R.string.prepared_activate))
                }
            }
        }
        outing.conditions?.let { c -> item { ConditionsCard(c) } }
        item { PlanCard(outing) }
        item { CarpCard { PrepResultLines(outing.result) } }
    }
}

/** Para cuándo, dónde y con cuántas cañas, y cuándo se preparó. */
@Composable
private fun SummaryCard(outing: PreparedOuting, now: Instant) {
    val cs = MaterialTheme.colorScheme
    CarpCard(containerColor = cs.primaryContainer) {
        Text(stringResource(R.string.prepared_target, Formatting.hour(outing.target)), style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer)
        val spot = outing.spotName?.let { if (outing.spotAuto) stringResource(R.string.prepared_spot_auto, it) else it } ?: "—"
        Text(
            stringResource(R.string.prepared_place, outing.reservoirName ?: stringResource(R.string.strategy_place_no_reservoir), spot, outing.rods),
            color = cs.onPrimaryContainer,
        )
        Caption(stringResource(R.string.prepared_at, Formatting.hour(outing.preparedAt)), color = cs.onPrimaryContainer)
        if (outing.target.isBefore(now)) Caption(stringResource(R.string.prepared_past), color = cs.error)
    }
}

/** Condiciones previstas a la hora de la salida: escala, nivel que limita, la cadena y los datos. */
@Composable
private fun ConditionsCard(c: PreparedConditions) {
    val cs = MaterialTheme.colorScheme
    CarpCard {
        Caption(stringResource(R.string.prepared_conditions_title))
        if (c.blocked) {
            Text(stringResource(R.string.prepared_blocked), style = MaterialTheme.typography.titleMedium, color = cs.error)
            return@CarpCard
        }
        Text(
            c.band?.let { stringResource(R.string.strategy_band_title, stringResource(it.labelRes())) } ?: stringResource(R.string.strategy_favorability_unknown),
            style = MaterialTheme.typography.titleLarge,
        )
        c.band?.let { BandScale(it) }
        c.limitingLevel?.let { level ->
            Pill(stringResource(R.string.strategy_limiting_chip, level.order, stringResource(level.titleRes())), cs.secondary, cs.onSecondary)
        }
        if (c.levels.isNotEmpty()) {
            Text(stringResource(R.string.prepared_levels_title), style = MaterialTheme.typography.titleSmall)
            c.levels.forEach { (level, value) ->
                Caption(stringResource(R.string.prepared_level, level.order, stringResource(level.titleRes()), Formatting.number(value, 2)))
            }
        }
        HorizontalDivider(color = cs.outlineVariant)
        c.waterC?.let { w ->
            Text(stringResource(if (c.waterSource == WaterTempSource.MEASURED) R.string.prepared_water_measured else R.string.prepared_water_estimated, Formatting.number(w)))
        }
        c.airC?.let { Text(stringResource(R.string.prepared_air, Formatting.number(it))) }
        c.windKmh?.let { wind ->
            Text(stringResource(R.string.prepared_wind, Formatting.number(wind, 0), Formatting.compass(c.windFromDeg)))
            c.gustKmh?.let { Caption(stringResource(R.string.prepared_gusts, Formatting.number(it, 0))) }
        }
        if (c.windows.isNotEmpty()) {
            Text(stringResource(R.string.prepared_windows_title), style = MaterialTheme.typography.titleSmall)
            c.windows.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.prepared_window, Formatting.clock(w.start), Formatting.clock(w.end), stringResource(w.kind.labelRes())), Modifier.weight(1f))
                    AnyEvidenceBadge(w.evidence)
                }
            }
        }
    }
}

/** El plan por caña tal como quedó, con el cebado y qué hacer si cambia algo. */
@Composable
private fun PlanCard(outing: PreparedOuting) {
    val cs = MaterialTheme.colorScheme
    CarpCard {
        Text(stringResource(R.string.prepared_plan_title), style = MaterialTheme.typography.titleMedium)
        if (!outing.hasPlan) {
            Text(stringResource(R.string.prepared_no_plan))
            return@CarpCard
        }
        outing.spotName?.let { Text(stringResource(R.string.strategy_plan_spot, it), style = MaterialTheme.typography.titleSmall) }
        Caption(stringResource(if (outing.planSource == PlanSource.AI) R.string.strategy_plan_by_ai_prepared else R.string.strategy_plan_by_app))
        outing.aiSummary?.let { Text(it) }
        outing.rodLines.forEach { r -> RodLineRow(r, outing.spotName) }

        HorizontalDivider(color = cs.outlineVariant)
        GroundbaitLines(outing.groundbait, outing.groundbaitAmount, outing.aiGroundbait, doubt = outing.groundbaitDoubt)

        if (outing.aiTips.isNotEmpty()) {
            Text(stringResource(R.string.strategy_plan_ai_tips), style = MaterialTheme.typography.titleSmall)
            outing.aiTips.forEach { a ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(a.text, modifier = Modifier.weight(1f))
                    a.evidence?.let { AnyEvidenceBadge(it) }
                }
            }
        }
        if (outing.contingencies.isNotEmpty()) {
            Text(stringResource(R.string.strategy_plan_if), style = MaterialTheme.typography.titleSmall)
            outing.contingencies.forEach { c ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column(Modifier.weight(1f)) { Text(contingencyText(c), style = MaterialTheme.typography.bodyMedium) }
                    AnyEvidenceBadge(c.evidence)
                }
            }
        }
        Caption(stringResource(R.string.strategy_disclaimer))
    }
}
