package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.foundation.background
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.advice.FishingAdvice
import com.nachojerez.carpstrategy.domain.advice.SpotPick
import com.nachojerez.carpstrategy.domain.assistant.AiAdvice
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.Spots
import com.nachojerez.carpstrategy.domain.guided.WindRelation
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
import com.nachojerez.carpstrategy.ui.guided.spotSummary
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
    StrategyContent(state, onStartGuided, onOpenSpots, plan, viewModel::requestPlan, onOpenAssistant, onOpenLessons, onOpenReservoir, onOpenReservoirs, viewModel::chooseSpot)
}

/**
 * Orden pensado para decidir en el puesto: resumen → qué hacer → cuándo → avisos → por qué
 * (plegado, para quien quiera ver la cadena de filtros y las reglas).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyContent(
    state: StrategyUiState,
    onStartGuided: () -> Unit = {},
    onOpenSpots: () -> Unit = {},
    plan: PlanUiState = PlanUiState(),
    onPlan: (Int) -> Unit = {},
    onOpenAssistant: () -> Unit = {},
    onOpenLessons: () -> Unit = {},
    onOpenReservoir: () -> Unit = {},
    onOpenReservoirs: () -> Unit = {},
    onChooseSpot: (Long?) -> Unit = {},
) {
    var whyOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScreenHeader(stringResource(R.string.strategy_title), stringResource(R.string.strategy_overline)) }
        // Dónde vas a pescar: embalse y puesto (fase 7.10). La estrategia se hace para ellos.
        if (!state.isLoading) item { PlaceCard(state, onOpenReservoirs, onChooseSpot, onOpenSpots) }
        when {
            state.isLoading -> item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Caption(stringResource(R.string.strategy_loading))
                }
            }
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
                // 1. Resumen: valoración y «qué hago ahora» (cebo, puesto, distancia…).
                item { ResultCard(result) }
                state.advice?.let { advice -> item { AdviceCard(advice, state.reservoir, state.pendingLessons, onOpenSpots, onOpenLessons, onOpenReservoir) } }
                if (!result.blocked) {
                    item { SummaryActions(state, onStartGuided) }

                    // Asistente de IA: plan por caña y consejos por apartado (validados por la app).
                    item { SectionTitle(stringResource(R.string.strategy_ai_section), subtitle = stringResource(R.string.strategy_ai_section_subtitle)) }
                    item {
                        if (plan.configured) {
                            AiPlanCard(plan, onPlan)
                        } else {
                            CarpCard {
                                Caption(stringResource(R.string.strategy_ai_off))
                                OutlinedButton(onClick = onOpenAssistant) { Text(stringResource(R.string.ai_settings_open)) }
                            }
                        }
                    }
                    plan.outcome?.value?.advice?.takeIf { it.isNotEmpty() }?.let { advice ->
                        item { AiAdviceCard(advice) }
                    }

                    // 2. Qué hacer: dónde (y tus puestos), presentación, cebado y notas.
                    item { SectionTitle(stringResource(R.string.strategy_what_title), subtitle = stringResource(R.string.strategy_what_subtitle)) }
                    WHAT_FIELDS.forEach { field ->
                        val advice = result.advice[field].orEmpty()
                        if (advice.isNotEmpty()) {
                            item { AdviceCard(stringResource(field.labelRes()), advice, avoid = false) }
                        }
                        if (field == StrategyField.WHERE) item { SpotsCard(state, onOpenSpots) }
                    }

                    // 3. Cuándo.
                    item { SectionTitle(stringResource(R.string.field_when), subtitle = stringResource(R.string.strategy_windows_subtitle)) }
                    item { WhenCard(state) }

                    // 4. Avisos: rotar puesto y qué evitar.
                    val avoid = result.advice[StrategyField.AVOID].orEmpty()
                    if (state.recentCatchZones.isNotEmpty() || avoid.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.strategy_warnings_title)) }
                        if (state.recentCatchZones.isNotEmpty()) item { RotateCard(state) }
                        if (avoid.isNotEmpty()) item { AdviceCard(stringResource(StrategyField.AVOID.labelRes()), avoid, avoid = true) }
                    }
                }

                // 5. Por qué (plegado): cadena de filtros, demanda, reglas y datos con peso 0.
                item {
                    TextButton(onClick = { whyOpen = !whyOpen }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (whyOpen) R.string.strategy_why_hide else R.string.strategy_why_show))
                    }
                }
                if (whyOpen) {
                    whyItems(result)
                    state.regulationReviewed?.let { date -> item { Caption(stringResource(R.string.strategy_regulation, date)) } }
                }
            }
        }
    }
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

/** Plan con IA: cuántas cañas, pedirlo y el resultado validado (o por qué se descartó). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiPlanCard(plan: PlanUiState, onPlan: (Int) -> Unit) {
    val res = LocalContext.current.resources
    var rods by rememberSaveable { mutableStateOf(2) }
    CarpCard {
        Text(stringResource(R.string.strategy_ai_plan), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Caption(stringResource(R.string.strategy_ai_rods))
            (1..GuidedSessions.MAX_RODS).forEach { n -> FilterChip(selected = rods == n, onClick = { rods = n }, label = { Text("$n") }) }
        }
        OutlinedButton(onClick = { onPlan(rods) }, enabled = !plan.running) {
            Text(stringResource(if (plan.running) R.string.strategy_ai_planning else R.string.strategy_ai_plan))
        }
        val outcome = plan.outcome
        val value = outcome?.value
        when {
            outcome == null -> Unit
            value != null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.strategy_ai_plan_title, Formatting.clock(outcome.exchange.time)),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    value.evidence?.let { AnyEvidenceBadge(it) }
                }
                Text(value.summary)
                value.rods.forEach { r ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.strategy_ai_plan_rod, r.rod), style = MaterialTheme.typography.titleSmall)
                            val parts = listOfNotNull(
                                r.spotName,
                                r.baitName,
                                r.rigName,
                                r.column?.let { stringResource(it.titleRes()) },
                                r.distanceM?.let { stringResource(R.string.strategy_ai_plan_distance, Formatting.number(it, 0)) },
                            )
                            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                            Caption(r.reason)
                        }
                        r.evidence?.let { AnyEvidenceBadge(it) }
                    }
                }
                // Los avisos de normativa no se muestran (fase 7.5): el horario ya lo controla la app.
                value.warnings.filterNot { it.evidence == Evidence.REGULATION }.forEach { w ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(w.text, modifier = Modifier.weight(1f))
                        w.evidence?.let { AnyEvidenceBadge(it) }
                    }
                }
                Caption(stringResource(R.string.strategy_ai_plan_note))
            }
            outcome.exchange.error != null -> Caption(stringResource(R.string.strategy_ai_plan_error))
            else -> Caption(
                stringResource(
                    R.string.strategy_ai_plan_invalid,
                    outcome.exchange.issues.map { res.getString(it.titleRes()) }.distinct().joinToString(", "),
                ),
            )
        }
    }
}

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

/** Próxima ventana legal sugerida y el botón para empezar la sesión guiada. */
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

/**
 * Embalse y puesto donde vas a pescar (fase 7.10). Cambiar de embalse es cambiar la ubicación
 * (como en Lugar); el puesto se recuerda por embalse y solo en este móvil.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceCard(state: StrategyUiState, onOpenReservoirs: () -> Unit, onChooseSpot: (Long?) -> Unit, onOpenSpots: () -> Unit) {
    CarpCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Caption(stringResource(R.string.reservoirs_place_title))
                Text(state.reservoir?.reservoir?.name ?: stringResource(R.string.strategy_place_no_reservoir), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onOpenReservoirs) { Text(stringResource(R.string.strategy_place_change)) }
        }
        Text(stringResource(R.string.guided_start_spot), style = MaterialTheme.typography.titleSmall)
        if (state.spots.isEmpty()) {
            Caption(stringResource(R.string.strategy_place_no_spots))
            TextButton(onClick = onOpenSpots) { Text(stringResource(R.string.spots_open)) }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(selected = state.chosenSpotId == null, onClick = { onChooseSpot(null) }, label = { Text(stringResource(R.string.strategy_place_auto)) })
                state.spots.forEach { spot ->
                    FilterChip(selected = state.chosenSpotId == spot.id, onClick = { onChooseSpot(spot.id) }, label = { Text(spot.name) })
                }
            }
            Caption(stringResource(if (state.chosenSpotId == null) R.string.strategy_place_auto_hint else R.string.strategy_place_spot_hint))
        }
    }
}

@Composable
private fun SummaryActions(state: StrategyUiState, onStartGuided: () -> Unit) {
    val now = state.result?.evaluatedAt ?: return
    CarpCard {
        val window = nextWindow(state.windows, now)
        if (window != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Caption(stringResource(R.string.strategy_next_window))
                    Text("${Formatting.clock(window.start)} – ${Formatting.clock(window.end)}", style = MaterialTheme.typography.titleLarge)
                    Caption(stringResource(window.kind.labelRes()))
                }
                AnyEvidenceBadge(window.evidence)
            }
        } else {
            Caption(stringResource(R.string.strategy_windows_none))
        }
        Button(onClick = onStartGuided, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.strategy_start_guided)) }
        Caption(stringResource(R.string.strategy_start_guided_hint))
    }
}

/** Tus puestos y cómo les da el viento dominante de las últimas 24 h (desempate, §5.4 🟡). */
@Composable
private fun SpotsCard(state: StrategyUiState, onOpenSpots: () -> Unit) {
    val wind = state.derived?.wind24h
    CarpCard {
        Text(stringResource(R.string.strategy_spots_title), style = MaterialTheme.typography.titleSmall)
        if (state.spots.isEmpty()) {
            Caption(stringResource(R.string.strategy_spots_empty))
        } else {
            wind?.let { Caption(stringResource(R.string.strategy_spots_wind, Formatting.compass(it.dominantDirectionDeg), Formatting.number(it.meanSpeedKmh, 0))) }
            state.spots.forEach { spot ->
                val relation = Spots.windRelation(spot.facingDeg, wind?.dominantDirectionDeg, wind?.meanSpeedKmh)
                Column {
                    Text(spot.name, style = MaterialTheme.typography.bodyLarge)
                    Caption(spotSummary(spot))
                    relation?.let { Caption(stringResource(it.shortRes())) }
                }
            }
        }
        TextButton(onClick = onOpenSpots) { Text(stringResource(R.string.spots_open)) }
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
