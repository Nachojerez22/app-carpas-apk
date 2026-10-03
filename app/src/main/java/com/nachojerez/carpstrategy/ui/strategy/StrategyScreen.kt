package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.derived.FreshnessLevel
import com.nachojerez.carpstrategy.domain.rules.ActiveRule
import com.nachojerez.carpstrategy.domain.rules.AdviceItem
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import com.nachojerez.carpstrategy.ui.components.AnyEvidenceBadge
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.components.ChainStatus
import com.nachojerez.carpstrategy.ui.components.DemandMeter
import com.nachojerez.carpstrategy.ui.components.FilterChainStep
import com.nachojerez.carpstrategy.ui.components.LegalWindowBar
import com.nachojerez.carpstrategy.ui.components.Pill
import com.nachojerez.carpstrategy.ui.components.ScreenHeader
import com.nachojerez.carpstrategy.ui.components.SectionTitle
import com.nachojerez.carpstrategy.ui.components.WarningChip
import com.nachojerez.carpstrategy.ui.components.WarningType
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.Spacing

@Composable
fun StrategyScreen(viewModel: StrategyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StrategyContent(state)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrategyContent(state: StrategyUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { ScreenHeader(stringResource(R.string.strategy_title), stringResource(R.string.strategy_overline)) }
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
                item { ResultCard(result) }

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

                    item { SectionTitle(stringResource(R.string.field_when), subtitle = stringResource(R.string.strategy_windows_subtitle)) }
                    item { WhenCard(state) }

                    ADVICE_FIELDS.forEach { field ->
                        val advice = result.advice[field].orEmpty()
                        if (advice.isNotEmpty()) {
                            item { SectionTitle(stringResource(field.labelRes())) }
                            item { AdviceCard(advice, avoid = field == StrategyField.AVOID) }
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
        }
        state.regulationReviewed?.let { date ->
            item { Caption(stringResource(R.string.strategy_regulation, date)) }
        }
    }
}

/** Orden de las secciones de consejo (Cuándo va aparte, con la barra de horario legal). */
private val ADVICE_FIELDS = listOf(StrategyField.WHERE, StrategyField.BAIT, StrategyField.PRESENTATION, StrategyField.AVOID, StrategyField.NOTES)

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
            result.blockingRules.forEach { Text(it.rule.description, color = cs.onErrorContainer) }
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
        Caption(stringResource(R.string.strategy_windows_legal_note))
    }
}

@Composable
private fun AdviceCard(advice: List<AdviceItem>, avoid: Boolean) {
    val cs = MaterialTheme.colorScheme
    val container = if (avoid) cs.errorContainer else cs.surface
    val content = if (avoid) cs.onErrorContainer else cs.onSurface
    CarpCard(containerColor = container) {
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
