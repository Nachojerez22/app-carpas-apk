package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.domain.advice.Contingency
import com.nachojerez.carpstrategy.domain.advice.ContingencyLine
import com.nachojerez.carpstrategy.domain.advice.DayPlan
import com.nachojerez.carpstrategy.domain.advice.DayPlans
import com.nachojerez.carpstrategy.domain.advice.FishingAdvice
import com.nachojerez.carpstrategy.domain.advice.PlanSource
import com.nachojerez.carpstrategy.domain.advice.RodLine
import com.nachojerez.carpstrategy.domain.advice.SpotPick
import com.nachojerez.carpstrategy.domain.assistant.AiAdvice
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiSpotPick
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.guided.WindRelation
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.rules.ActiveRule
import com.nachojerez.carpstrategy.domain.rules.AdviceItem
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ChainStatus
import com.nachojerez.carpstrategy.ui.components.DemandMeter
import com.nachojerez.carpstrategy.ui.components.EvidenceBadge
import com.nachojerez.carpstrategy.ui.components.FilterChainStep
import com.nachojerez.carpstrategy.ui.components.LegalWindowBar
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.components.WarningChip
import com.nachojerez.carpstrategy.ui.components.WarningType
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.diary.zoneLabelRes
import com.nachojerez.carpstrategy.ui.guided.baitText
import com.nachojerez.carpstrategy.ui.guided.shortRes
import com.nachojerez.carpstrategy.ui.guided.titleRes
import com.nachojerez.carpstrategy.ui.reservoir.effectRes
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Duration

@Composable
fun StrategyScreen(
    onStartGuided: () -> Unit = {},
    onOpenSpots: () -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenLessons: () -> Unit = {},
    onOpenReservoir: () -> Unit = {},
    onOpenReservoirs: () -> Unit = {},
    viewModel: StrategyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    StrategyContent(
        state, plan,
        StrategyActions(
            onStartGuided = onStartGuided,
            onOpenSpots = onOpenSpots,
            onOpenAssistant = onOpenAssistant,
            onOpenLessons = onOpenLessons,
            onOpenReservoir = onOpenReservoir,
            onOpenReservoirs = onOpenReservoirs,
            onChooseReservoir = viewModel::chooseReservoir,
            onChooseSpot = viewModel::chooseSpot,
            onChooseRods = viewModel::chooseRods,
            onAskAgain = viewModel::requestPlan,
        ),
    )
}

/** Lo que se puede hacer desde Estrategia (navegación y elecciones). */
data class StrategyActions(
    val onStartGuided: () -> Unit = {},
    val onOpenSpots: () -> Unit = {},
    val onOpenAssistant: () -> Unit = {},
    val onOpenLessons: () -> Unit = {},
    val onOpenReservoir: () -> Unit = {},
    val onOpenReservoirs: () -> Unit = {},
    val onChooseReservoir: (Reservoir) -> Unit = {},
    val onChooseSpot: (Long?) -> Unit = {},
    val onChooseRods: (Int) -> Unit = {},
    val onAskAgain: () -> Unit = {},
)

/**
 * Estrategia en 3 pasos (fase 7.12): ① embalse → ② puesto y cañas → ③ un solo plan para hoy
 * (de la app con el conocimiento y tus datos, afinado por la IA) con qué hacer si cambia algo.
 * Arriba, siempre, «Condiciones para hoy» (escala y nivel que limita). Lo técnico (reglas,
 * ventanas, cadena de filtros) va plegado en «Detalles» y, al
 * final, cuando ya está todo elegido, se empieza la sesión guiada con ese plan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyContent(state: StrategyUiState, plan: PlanUiState = PlanUiState(), actions: StrategyActions = StrategyActions()) {
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val dayPlan = remember(state, plan) { dayPlanFor(state, plan) }
    // El plan de la IA solo cuenta si se pidió para lo que tienes elegido ahora.
    val currentAi = plan.outcome?.takeIf { plan.key != null && plan.key == planKey(state) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScreenHeader(stringResource(R.string.strategy_title), stringResource(R.string.strategy_overline)) }
        if (state.isLoading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Caption(stringResource(R.string.strategy_loading))
                }
            }
            return@LazyColumn
        }
        // Condiciones para hoy (escala de 5 tramos y nivel que limita): siempre arriba.
        state.result?.let { result -> item { ResultCard(result) } }
        // ① Embalse.
        item { ReservoirStep(state, actions) }
        // ② Puesto (con la recomendación de la app o de la IA) y cañas.
        item { SpotStep(state, dayPlan, currentAi?.value?.bestSpot, actions) }
        when {
            state.ruleIssues.isNotEmpty() -> {
                item {
                    CarpCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                        Text(stringResource(R.string.strategy_rules_invalid), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(state.ruleIssues) { Caption(ruleIssueText(it)) }
            }
            state.result == null -> item {
                CarpCard {
                    Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.strategy_no_data))
                }
            }
            else -> {
                val result = state.result
                if (state.forecastFreshness?.level == FreshnessLevel.STALE) {
                    item {
                        FlowRow {
                            WarningChip(WarningType.Stale, stringResource(R.string.strategy_stale), painterResource(R.drawable.ic_clock))
                        }
                    }
                }
                // ③ Tu plan para hoy.
                if (!result.blocked && dayPlan != null && state.advice != null) {
                    item { PlanStep(state, plan, currentAi, dayPlan, state.advice, actions) }
                }

                // Detalles (plegado): todo lo que hay detrás del plan.
                item {
                    TextButton(onClick = { detailsOpen = !detailsOpen }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (detailsOpen) R.string.strategy_details_hide else R.string.strategy_details_show))
                    }
                }
                if (detailsOpen) detailItems(state, plan, result, actions)

                // Al final, con todo elegido: empezar la sesión guiada con este plan.
                if (!result.blocked && dayPlan != null) item { StartStep(state, dayPlan, actions.onStartGuided) }
            }
        }
    }
}

/** «Detalles»: «Qué hago ahora» completo, consejos de la IA y de las reglas, horas, avisos y el porqué. */
private fun LazyListScope.detailItems(state: StrategyUiState, plan: PlanUiState, result: StrategyResult, actions: StrategyActions) {
    state.advice?.let { advice -> item { AdviceCard(advice, state.reservoir, state.pendingLessons, actions.onOpenSpots, actions.onOpenLessons, actions.onOpenReservoir) } }
    if (!result.blocked) {
        plan.outcome?.value?.advice?.takeIf { it.isNotEmpty() }?.let { advice -> item { AiAdviceCard(advice) } }
        item { SectionTitle(stringResource(R.string.strategy_what_title), subtitle = stringResource(R.string.strategy_what_subtitle)) }
        WHAT_FIELDS.forEach { field ->
            val advice = result.advice[field].orEmpty()
            if (advice.isNotEmpty()) item { AdviceCard(stringResource(field.labelRes()), advice, avoid = false) }
        }
        item { SectionTitle(stringResource(R.string.field_when), subtitle = stringResource(R.string.strategy_windows_subtitle)) }
        item { WhenCard(state) }
        val avoid = result.advice[StrategyField.AVOID].orEmpty()
        if (state.recentCatchZones.isNotEmpty() || avoid.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.strategy_warnings_title)) }
            if (state.recentCatchZones.isNotEmpty()) item { RotateCard(state) }
            if (avoid.isNotEmpty()) item { AdviceCard(stringResource(StrategyField.AVOID.labelRes()), avoid, avoid = true) }
        }
    }
    whyItems(result)
    state.regulationReviewed?.let { date -> item { Caption(stringResource(R.string.strategy_regulation, date)) } }
}

/** La parte «Por qué»: cadena de filtros, demanda de alimento, reglas activas y datos con peso 0. */
private fun LazyListScope.whyItems(result: StrategyResult) {
    item { SectionTitle(stringResource(R.string.strategy_chain_title), subtitle = stringResource(R.string.strategy_chain_subtitle)) }
    item {
        CarpCard(contentPadding = Spacing.sm) {
            chainRows(result).forEach { row ->
                FilterChainStep(
                    number = row.level.order,
                    title = stringResource(row.level.titleRes()),
                    status = row.status.toChainStatus(),
                    why = row.why,
                )
            }
        }
    }
    if (!result.blocked) {
        item { SectionTitle(stringResource(R.string.strategy_demand_title), subtitle = stringResource(R.string.strategy_demand_subtitle)) }
        item {
            CarpCard {
                Text(
                    result.demand?.let { stringResource(it.labelRes()) } ?: stringResource(R.string.strategy_demand_unknown),
                    style = MaterialTheme.typography.titleMedium,
                )
                DemandMeter(result.demand)
            }
        }
    }
    item { SectionTitle(stringResource(R.string.strategy_why_title), subtitle = stringResource(R.string.strategy_why_subtitle)) }
    item {
        CarpCard {
            val rules = result.activeRules.filter { it.rule.type != RuleType.RECORD }
            if (rules.isEmpty()) Caption(stringResource(R.string.strategy_no_rules))
            rules.forEachIndexed { i, rule ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                RuleRow(rule)
            }
        }
    }
    val recorded = result.activeRules.filter { it.rule.type == RuleType.RECORD }
    if (recorded.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.strategy_recorded_title), subtitle = stringResource(R.string.strategy_recorded_subtitle)) }
        item {
            CarpCard {
                recorded.forEachIndexed { i, rule ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RuleRow(rule)
                }
            }
        }
    }
    if (result.notEvaluable.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.strategy_not_evaluable_title)) }
        item {
            CarpCard {
                result.notEvaluable.forEach { (rule, missing) ->
                    Caption(
                        stringResource(
                            R.string.strategy_not_evaluable,
                            rule.id,
                            missing.map { stringResource(it.labelRes()) }.joinToString(", "),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * «Qué hago ahora» (fase 7.5): cebos para comparar entre cañas, presentación, puestos ordenados
 * con distancia y profundidad, cebado, tus mejores momentos y tus lecciones.
 */
@Composable
private fun AdviceCard(
    advice: FishingAdvice,
    reservoir: ReservoirContext?,
    pendingLessons: Int,
    onOpenSpots: () -> Unit,
    onOpenLessons: () -> Unit,
    onOpenReservoir: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    CarpCard {
        val now = advice.forTime
        val title = if (advice.deferred) stringResource(R.string.advice_title_next, Formatting.clock(now)) else stringResource(R.string.advice_title)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Caption(stringResource(R.string.advice_moment, stringResource(advice.dayPart.titleRes()), Formatting.clock(now)))
        reservoir?.let { r ->
            Text(stringResource(R.string.advice_reservoir, r.reservoir.name), style = MaterialTheme.typography.titleSmall, color = cs.primary)
            if (r.sheet == null && !r.isBrovales) {
                Caption(stringResource(R.string.advice_no_sheet))
                TextButton(onClick = onOpenReservoir) { Text(stringResource(R.string.reservoir_sheet_open)) }
            }
        }

        Text(stringResource(R.string.advice_baits), style = MaterialTheme.typography.titleSmall)
        advice.baits.forEachIndexed { i, b ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. " + (res.baitText(b.type, b.gearName) ?: "—"), style = MaterialTheme.typography.bodyLarge)
                    b.proven?.let { Caption(stringResource(R.string.advice_bait_proven, it.catches, it.sessions)) }
                    if (b.gearName == null) Caption(stringResource(R.string.advice_bait_not_in_gear))
                }
                AnyEvidenceBadge(b.evidence)
            }
        }
        val column = stringResource(advice.column.titleRes()) + if (advice.columnProven) " " + stringResource(R.string.advice_column_proven) else ""
        Text(stringResource(R.string.advice_column, column))
        advice.altColumn?.let { Caption(stringResource(R.string.advice_alt_column, stringResource(it.titleRes()))) }
        val groundbait = advice.groundbait
        Text(if (groundbait == null) stringResource(R.string.advice_no_groundbait) else stringResource(R.string.advice_groundbait, stringResource(groundbait.titleRes())))
        if (advice.groundbaitDoubt) Caption(stringResource(R.string.advice_groundbait_doubt), color = cs.error)
        if (advice.species.notes.isNotEmpty()) {
            Text(stringResource(R.string.advice_species), style = MaterialTheme.typography.titleSmall)
            advice.species.notes.forEach { (sp, evidence) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Caption(stringResource(sp.effectRes()), Modifier.weight(1f))
                    AnyEvidenceBadge(evidence)
                }
            }
        }

        val chosen = advice.chosen
        Text(stringResource(if (chosen != null) R.string.advice_chosen_title else R.string.advice_spots), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.advice_where_generic, stringResource(advice.depth.titleRes()), stringResource(advice.distance.titleRes())))
        if (chosen != null) {
            // Puesto elegido: todo para él; si otro encaja claramente mejor hoy, solo se avisa.
            Text(chosen.spot.name, style = MaterialTheme.typography.bodyLarge, color = cs.primary)
            Caption(spotPickText(chosen))
            chosen.spot.fetchM?.let { Caption(stringResource(R.string.advice_spot_fetch, Formatting.number(it, 0))) }
            Caption(stringResource(spotWindRes(chosen)))
            chosen.windTips.forEach { Caption(stringResource(it.titleRes())) }
            advice.better?.let { b -> Caption(stringResource(R.string.advice_better_spot, b.spot.name, spotPickText(b)), color = CarpTheme.colors.onWarnContainer) }
        } else {
            if (advice.spots.isEmpty()) {
                Caption(stringResource(R.string.advice_no_spots))
                TextButton(onClick = onOpenSpots) { Text(stringResource(R.string.spots_open)) }
            }
            advice.spots.take(MAX_SPOTS).forEachIndexed { i, p ->
                Text("${i + 1}. ${p.spot.name}", style = MaterialTheme.typography.bodyLarge, color = if (i == 0) cs.primary else cs.onSurface)
                Caption(spotPickText(p))
                if (i == 0) p.windTips.forEach { Caption(stringResource(it.titleRes())) }
            }
        }

        if (advice.bestDayParts.isNotEmpty()) {
            Text(stringResource(R.string.advice_best_parts, advice.bestDayParts.joinToString(", ") { res.getString(it.value.titleRes()).lowercase() }))
        }
        if (advice.lessons.isNotEmpty()) {
            Text(stringResource(R.string.advice_lessons), style = MaterialTheme.typography.titleSmall)
            advice.lessons.take(MAX_LESSONS).forEach { l ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(l.text, modifier = Modifier.weight(1f))
                    AnyEvidenceBadge(l.evidence)
                }
            }
        }
        val exp = advice.experience
        Caption(
            if (exp.sessions > 0) stringResource(R.string.advice_experience, exp.sessions, exp.catches) else stringResource(R.string.advice_experience_none),
        )
        if (exp.otherReservoirs) Caption(stringResource(R.string.advice_other_reservoirs))
        TextButton(onClick = onOpenLessons) {
            Text(if (pendingLessons > 0) stringResource(R.string.lessons_open_pending, pendingLessons) else stringResource(R.string.advice_open_lessons))
        }
    }
}

private const val MAX_SPOTS = 4
private const val MAX_LESSONS = 4
private const val MAX_AI_TIPS = 3

/** Consejos de la IA agrupados por apartado (dónde, cuándo, cebado…), cada uno con su evidencia. */
@Composable
private fun AiAdviceCard(advice: List<AiAdvice>) {
    val cs = MaterialTheme.colorScheme
    CarpCard {
        Text(stringResource(R.string.strategy_ai_advice_title), style = MaterialTheme.typography.titleSmall)
        StrategyField.entries.forEach { field ->
            val items = advice.filter { it.field == field }
            if (items.isNotEmpty()) {
                val avoid = field == StrategyField.AVOID
                Text(stringResource(field.labelRes()), style = MaterialTheme.typography.labelLarge, color = if (avoid) cs.error else cs.primary)
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(item.text, modifier = Modifier.weight(1f))
                        item.evidence?.let { AnyEvidenceBadge(it) }
                    }
                }
            }
        }
        Caption(stringResource(R.string.strategy_ai_advice_note))
    }
}

/** «Primera caída · encaje 2/3 · a 40 m · 3,5 m de fondo · viento a favor · te ha funcionado». */
@Composable
private fun spotPickText(p: SpotPick): String = listOfNotNull(
    stringResource(R.string.advice_spot_reasons, stringResource(p.spot.structure.titleRes()), p.structureFit),
    p.spot.distanceM?.let { stringResource(R.string.advice_spot_distance, Formatting.number(it, 0)) },
    p.spot.depthM?.let { stringResource(R.string.advice_spot_depth, Formatting.number(it)) },
    if (p.windBonus) stringResource(R.string.advice_spot_wind) else null,
    p.proven?.let { stringResource(R.string.advice_spot_proven, it.catches) },
).joinToString(" · ")

/** Cómo le da el viento al puesto elegido (desempate, §5.4). */
private fun spotWindRes(p: SpotPick): Int = when {
    p.windBonus -> R.string.spot_wind_favoured
    p.spot.facingDeg == null -> R.string.advice_spot_no_facing
    else -> (p.wind ?: WindRelation.CALM).shortRes()
}

/** Número de paso y título («① Embalse»). */
@Composable
private fun StepTitle(number: Int, title: String) {
    Text(stringResource(R.string.strategy_step, number, title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * ① Embalse: desplegable con tus embalses (los que tienen puestos) y «Otro embalse…» para la
 * lista completa. Cambiar de embalse es cambiar la ubicación, como en Lugar.
 */
@Composable
private fun ReservoirStep(state: StrategyUiState, actions: StrategyActions) {
    var menuOpen by remember { mutableStateOf(false) }
    CarpCard {
        StepTitle(1, stringResource(R.string.reservoirs_place_title))
        Box {
            OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    (state.reservoir?.reservoir?.name ?: stringResource(R.string.strategy_place_no_reservoir)) + " ▾",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                state.myReservoirs.forEach { (reservoir, count) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(reservoir.name)
                                Caption(pluralStringResource(R.plurals.strategy_place_spots_count, count, count))
                            }
                        },
                        onClick = {
                            menuOpen = false
                            if (reservoir.id != state.reservoir?.reservoir?.id) actions.onChooseReservoir(reservoir)
                        },
                    )
                }
                if (state.myReservoirs.isNotEmpty()) HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.strategy_place_other)) },
                    onClick = {
                        menuOpen = false
                        actions.onOpenReservoirs()
                    },
                )
            }
        }
        state.reservoir?.takeIf { it.sheet == null && !it.isBrovales }?.let {
            Caption(stringResource(R.string.advice_no_sheet))
            TextButton(onClick = actions.onOpenReservoir) { Text(stringResource(R.string.reservoir_sheet_open)) }
        }
    }
}

/**
 * ② Puesto y cañas: la recomendación (de la IA si ya ha contestado para lo elegido; si no, de la
 * app) con su porqué y un botón para cambiarte; tus puestos del embalse y cuántas cañas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpotStep(state: StrategyUiState, dayPlan: DayPlan?, aiBest: AiSpotPick?, actions: StrategyActions) {
    val cs = MaterialTheme.colorScheme
    CarpCard {
        StepTitle(2, stringResource(R.string.guided_start_spot))
        if (state.spots.isEmpty()) {
            Caption(stringResource(R.string.strategy_place_no_spots))
            TextButton(onClick = actions.onOpenSpots) { Text(stringResource(R.string.spots_open)) }
        } else {
            val appBest = state.advice?.spots?.firstOrNull()
            val aiSpot = aiBest?.let { b -> state.spots.firstOrNull { it.name.trim().equals(b.spotName.trim(), ignoreCase = true) } }
            val recommended = aiSpot ?: appBest?.spot
            if (recommended != null) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Column(Modifier.weight(1f)) {
                        Caption(stringResource(if (aiSpot != null) R.string.strategy_recommended_ai else R.string.strategy_recommended_app))
                        Text(recommended.name, style = MaterialTheme.typography.titleMedium, color = cs.primary)
                        if (aiSpot != null && aiBest != null) Caption(aiBest.reason) else appBest?.let { Caption(spotPickText(it)) }
                    }
                    (if (aiSpot != null) aiBest?.evidence else Evidence.YELLOW)?.let { AnyEvidenceBadge(it) }
                }
                if (state.chosenSpotId != null && state.chosenSpotId != recommended.id) {
                    OutlinedButton(onClick = { actions.onChooseSpot(recommended.id) }) { Text(stringResource(R.string.strategy_ai_best_use, recommended.name)) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = state.chosenSpotId == null, onClick = { actions.onChooseSpot(null) }, label = { Text(stringResource(R.string.strategy_place_auto)) })
                state.spots.forEach { spot ->
                    FilterChip(selected = state.chosenSpotId == spot.id, onClick = { actions.onChooseSpot(spot.id) }, label = { Text(spot.name) })
                }
            }
            if (state.chosenSpotId == null) dayPlan?.spot?.let { Caption(stringResource(R.string.strategy_auto_spot, it.spot.name)) }
        }
        Text(stringResource(R.string.strategy_rods_title), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (1..DayPlans.MAX_RODS).forEach { n -> FilterChip(selected = state.rods == n, onClick = { actions.onChooseRods(n) }, label = { Text("$n") }) }
        }
    }
}

/**
 * ③ Tu plan para hoy: condiciones y mejor rato, de dónde sale el plan (app o IA), una línea por
 * caña con su porqué y evidencia, cebado y qué hacer si cambia algo.
 */
@Composable
private fun PlanStep(
    state: StrategyUiState,
    plan: PlanUiState,
    currentAi: AiOutcome<AiPlan>?,
    dayPlan: DayPlan,
    advice: FishingAdvice,
    actions: StrategyActions,
) {
    val cs = MaterialTheme.colorScheme
    val res = LocalContext.current.resources
    CarpCard {
        StepTitle(3, if (advice.deferred) stringResource(R.string.strategy_plan_title_next, Formatting.clock(advice.forTime)) else stringResource(R.string.strategy_plan_title))
        // Mejor rato (las condiciones están arriba, en su tarjeta).
        state.result?.evaluatedAt?.let { now ->
            nextWindow(state.windows, now)?.let { w ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(stringResource(R.string.strategy_best_time, Formatting.clock(w.start), Formatting.clock(w.end), stringResource(w.kind.labelRes()).lowercase()), modifier = Modifier.weight(1f))
                    AnyEvidenceBadge(w.evidence)
                }
            }
        }
        dayPlan.spot?.let { p ->
            Text(stringResource(R.string.strategy_plan_spot, p.spot.name), style = MaterialTheme.typography.titleSmall)
            Caption(spotPickText(p))
        }

        // De dónde sale el plan.
        HorizontalDivider(color = cs.outlineVariant)
        when {
            plan.running -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Caption(stringResource(R.string.strategy_plan_asking))
            }
            dayPlan.source == PlanSource.AI -> Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(stringResource(R.string.strategy_plan_by_ai, Formatting.clock(currentAi?.exchange?.time ?: advice.forTime)), Modifier.weight(1f))
                TextButton(onClick = actions.onAskAgain) { Text(stringResource(R.string.strategy_plan_ask_again)) }
            }
            !plan.configured -> {
                Caption(stringResource(R.string.strategy_plan_by_app_no_ai))
                TextButton(onClick = actions.onOpenAssistant) { Text(stringResource(R.string.ai_settings_open)) }
            }
            currentAi != null && currentAi.value == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(
                    if (currentAi.exchange.error != null) stringResource(R.string.strategy_plan_ai_error)
                    else stringResource(R.string.strategy_plan_ai_invalid, currentAi.exchange.issues.map { res.getString(it.titleRes()) }.distinct().joinToString(", ")),
                    Modifier.weight(1f),
                )
                TextButton(onClick = actions.onAskAgain) { Text(stringResource(R.string.strategy_plan_ask_again)) }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(stringResource(R.string.strategy_plan_by_app), Modifier.weight(1f))
                TextButton(onClick = actions.onAskAgain) { Text(stringResource(R.string.strategy_plan_ask_ai)) }
            }
        }
        dayPlan.aiSummary?.let { Text(it) }

        // Una línea por caña.
        dayPlan.rods.forEach { r -> RodLineRow(r, dayPlan.spot?.spot?.name) }

        // Cebado y especies.
        HorizontalDivider(color = cs.outlineVariant)
        val groundbait = dayPlan.groundbait
        Text(if (groundbait == null) stringResource(R.string.advice_no_groundbait) else stringResource(R.string.advice_groundbait, stringResource(groundbait.titleRes())))
        if (advice.groundbaitDoubt) Caption(stringResource(R.string.advice_groundbait_doubt), color = cs.error)
        advice.species.notes.forEach { (sp, evidence) ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Caption(stringResource(sp.effectRes()), Modifier.weight(1f))
                AnyEvidenceBadge(evidence)
            }
        }

        // La IA añade (como mucho 3 consejos), y qué hacer si cambia algo.
        currentAi?.value?.advice?.filter { it.field != StrategyField.WHERE }?.take(MAX_AI_TIPS)?.takeIf { it.isNotEmpty() }?.let { tips ->
            Text(stringResource(R.string.strategy_plan_ai_tips), style = MaterialTheme.typography.titleSmall)
            tips.forEach { a ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(a.text, modifier = Modifier.weight(1f))
                    a.evidence?.let { AnyEvidenceBadge(it) }
                }
            }
        }
        if (dayPlan.contingencies.isNotEmpty()) {
            Text(stringResource(R.string.strategy_plan_if), style = MaterialTheme.typography.titleSmall)
            dayPlan.contingencies.forEach { c ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(contingencyText(c), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    AnyEvidenceBadge(c.evidence)
                }
            }
        }
        if (advice.lessons.isNotEmpty() || state.pendingLessons > 0) {
            TextButton(onClick = actions.onOpenLessons) {
                Text(
                    if (state.pendingLessons > 0) stringResource(R.string.lessons_open_pending, state.pendingLessons)
                    else stringResource(R.string.strategy_plan_lessons, advice.lessons.size),
                )
            }
        }
        Caption(stringResource(R.string.strategy_disclaimer))
    }
}

/** «Caña 1 · Chufa (chufa) · pop-up · a 40 m» y debajo el porqué (de la IA o de tus datos). */
@Composable
private fun RodLineRow(r: RodLine, planSpot: String?) {
    val res = LocalContext.current.resources
    var open by rememberSaveable(r.rod) { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.strategy_ai_plan_rod, r.rod), style = MaterialTheme.typography.titleSmall)
            val parts = listOfNotNull(
                r.spotName?.takeIf { !it.trim().equals(planSpot?.trim(), ignoreCase = true) },
                res.baitText(r.baitType, r.baitName),
                r.rigName,
                stringResource(r.column.titleRes()),
                r.distanceM?.let { stringResource(R.string.strategy_ai_plan_distance, Formatting.number(it, 0)) } ?: stringResource(r.distance.titleRes()),
            )
            Text(parts.joinToString(" · "))
            if (open) {
                val why = r.reason
                    ?: r.proven?.let { stringResource(R.string.advice_bait_proven, it.catches, it.sessions) }
                    ?: stringResource(R.string.strategy_rod_why_app, stringResource(r.depth.titleRes()), stringResource(r.distance.titleRes()))
                Caption(why)
                if (r.baitName == null && r.source == PlanSource.APP) Caption(stringResource(R.string.advice_bait_not_in_gear))
            } else {
                Caption(stringResource(R.string.strategy_rod_why_show))
            }
        }
        AnyEvidenceBadge(r.evidence)
    }
}

/** Qué hacer si pasa algo, en una frase. */
@Composable
private fun contingencyText(c: ContingencyLine): String {
    val after = c.after?.let { durationText(it) }
    return when (c.kind) {
        Contingency.NO_SIGNALS -> stringResource(
            R.string.strategy_if_no_signals,
            after.orEmpty(),
            stringResource(if (c.step == StepKind.COLUMN) R.string.strategy_if_step_column else R.string.strategy_if_step_distance),
        )
        Contingency.NO_SIGNALS_ZONE -> stringResource(R.string.strategy_if_no_signals_zone, after.orEmpty())
        Contingency.SIGNALS_NO_BITES -> stringResource(R.string.strategy_if_signals, after.orEmpty())
        Contingency.CARP_UP -> stringResource(R.string.strategy_if_carp_up)
        Contingency.OTHER_SPECIES -> stringResource(if (c.step == StepKind.ANTI_CRAB) R.string.strategy_if_crab else R.string.strategy_if_other_species)
        Contingency.WIND -> c.windTip?.let { stringResource(it.titleRes()) }.orEmpty()
    }
}

/** «45 min», «1 h», «1 h 30 min». */
@Composable
private fun durationText(d: Duration): String {
    val h = d.toHours().toInt()
    val m = (d.toMinutes() % 60).toInt()
    return when {
        h == 0 -> stringResource(R.string.duration_m, m)
        m == 0 -> stringResource(R.string.duration_h, h)
        else -> stringResource(R.string.duration_hm, h, m)
    }
}

/**
 * Final del camino: con embalse, puesto, cañas y plan ya elegidos, empezar la sesión guiada.
 * Viene con el puesto y las cañas marcados, y el plan de la IA se copia en la sesión.
 */
@Composable
private fun StartStep(state: StrategyUiState, dayPlan: DayPlan, onStartGuided: () -> Unit) {
    CarpCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            stringResource(R.string.strategy_start_summary, state.reservoir?.reservoir?.name ?: stringResource(R.string.strategy_place_no_reservoir), dayPlan.spot?.spot?.name ?: "—", state.rods),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Button(onClick = onStartGuided, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strategy_start_with_plan)) }
        Caption(stringResource(R.string.strategy_start_guided_hint), color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

/** 🟢 Capturas propias recientes en un puesto: sugerir rotar puesto y montaje (§5.8). */
@Composable
private fun RotateCard(state: StrategyUiState) {
    val now = state.result?.evaluatedAt ?: return
    CarpCard(containerColor = CarpTheme.colors.warnContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.strategy_rotate_title),
                style = MaterialTheme.typography.titleMedium,
                color = CarpTheme.colors.onWarnContainer,
                modifier = Modifier.weight(1f),
            )
            EvidenceBadge(EvidenceKind.Strong, short = true)
        }
        state.recentCatchZones.entries.sortedByDescending { it.value }.forEach { (zone, last) ->
            Text(
                stringResource(R.string.strategy_rotate_zone, stringResource(zone.zoneLabelRes()), Duration.between(last, now).toDays().coerceAtLeast(0).toInt()),
                color = CarpTheme.colors.onWarnContainer,
            )
        }
        Caption(stringResource(R.string.strategy_rotate_why), color = CarpTheme.colors.onWarnContainer)
    }
}

/** «Qué hacer», en este orden (Cuándo va aparte, con la barra de horario legal; Evitar, en Avisos). */
private val WHAT_FIELDS = listOf(StrategyField.WHERE, StrategyField.PRESENTATION, StrategyField.BAIT, StrategyField.NOTES)

private fun LevelStatus.toChainStatus(): ChainStatus = when (this) {
    LevelStatus.PASSED -> ChainStatus.Passed
    LevelStatus.LIMITS -> ChainStatus.Limits
    LevelStatus.REDUCES -> ChainStatus.Reduces
    LevelStatus.NEUTRAL -> ChainStatus.Neutral
    LevelStatus.BLOCKS -> ChainStatus.Blocks
}

@Composable
private fun ResultCard(result: StrategyResult) {
    val cs = MaterialTheme.colorScheme
    if (result.blocked) {
        CarpCard(containerColor = cs.errorContainer) {
            Text(stringResource(R.string.strategy_blocked), color = cs.onErrorContainer, style = MaterialTheme.typography.headlineSmall)
            Caption(stringResource(R.string.strategy_disclaimer), color = cs.onErrorContainer)
        }
        return
    }
    CarpCard(containerColor = cs.primaryContainer) {
        Caption(stringResource(R.string.strategy_result_overline), color = cs.onPrimaryContainer)
        val band = result.band
        Text(
            band?.let { stringResource(R.string.strategy_band_title, stringResource(it.labelRes())) } ?: stringResource(R.string.strategy_favorability_unknown),
            style = MaterialTheme.typography.headlineMedium,
            color = cs.onPrimaryContainer,
        )
        if (band != null) BandScale(band)
        result.limitingLevel?.let { level ->
            Pill(
                stringResource(R.string.strategy_limiting_chip, level.order, stringResource(level.titleRes())),
                cs.secondary,
                cs.onSecondary,
            )
        }
        Caption(stringResource(R.string.strategy_disclaimer), color = cs.onPrimaryContainer)
    }
}

/** Escala de 5 tramos; el tramo actual relleno. El texto acompaña siempre al color. */
@Composable
private fun BandScale(band: FavorabilityBand) {
    val cs = MaterialTheme.colorScheme
    val index = band.ordinal
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FavorabilityBand.entries.forEach { b ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(if (b.ordinal == index) 12.dp else 8.dp)
                        .background(if (b.ordinal <= index) cs.primary else cs.surface, RoundedCornerShape(4.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Caption(stringResource(R.string.band_very_unfavorable), modifier = Modifier.weight(1f), color = cs.onPrimaryContainer)
            Caption(stringResource(R.string.band_very_favorable), color = cs.onPrimaryContainer)
        }
    }
}

@Composable
private fun WhenCard(state: StrategyUiState) {
    val derived = state.derived
    CarpCard {
        if (derived != null) {
            LegalWindowBar(derived.legalToday, derived.sunToday, state.result?.evaluatedAt ?: derived.now, highlights = state.windows)
        }
        if (state.windows.isEmpty()) Caption(stringResource(R.string.strategy_windows_none))
        state.windows.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text("${Formatting.clock(w.start)} – ${Formatting.clock(w.end)}", style = MaterialTheme.typography.titleMedium)
                    Caption(stringResource(w.kind.labelRes()))
                }
                AnyEvidenceBadge(w.evidence)
            }
        }
    }
}

@Composable
private fun AdviceCard(title: String, advice: List<AdviceItem>, avoid: Boolean) {
    val cs = MaterialTheme.colorScheme
    val container = if (avoid) cs.errorContainer else cs.surface
    val content = if (avoid) cs.onErrorContainer else cs.onSurface
    CarpCard(containerColor = container) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = content)
        advice.forEachIndexed { i, item ->
            if (i > 0) HorizontalDivider(color = if (avoid) content.copy(alpha = 0.2f) else cs.outlineVariant)
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(item.text, color = content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                AnyEvidenceBadge(item.evidence)
            }
        }
    }
}

@Composable
private fun RuleRow(active: ActiveRule) {
    val rule = active.rule
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(rule.id, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            effectText(active)?.let { Pill(it, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant) }
            AnyEvidenceBadge(rule.evidence)
        }
        Text(rule.description, style = MaterialTheme.typography.bodyMedium)
        active.checks.forEach { Caption(checkText(it)) }
        if (rule.type == RuleType.MEMBERSHIP && active.membershipInput != null) {
            Caption(
                stringResource(
                    R.string.strategy_rule_membership,
                    stringResource(rule.membership!!.parameter.labelRes()),
                    Formatting.number(active.membershipInput),
                    Formatting.number(active.appliedFactor, 2),
                ),
            )
        }
    }
}

/** Efecto de la regla: «× 0,85» si resta, o su tipo si no modifica la valoración. */
@Composable
private fun effectText(active: ActiveRule): String? = when {
    active.rule.type == RuleType.HARD_FILTER -> stringResource(R.string.strategy_effect_blocks)
    active.appliedFactor < 1.0 -> stringResource(R.string.strategy_rule_factor, Formatting.number(active.appliedFactor, 2))
    active.rule.type == RuleType.WARNING -> stringResource(R.string.strategy_effect_warning)
    active.rule.type == RuleType.RECORD -> stringResource(R.string.strategy_effect_record)
    active.rule.type == RuleType.ADVICE -> stringResource(R.string.strategy_effect_advice)
    else -> null
}
