package com.nachojerez.carpstrategy.ui.strategy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.rules.ActiveRule
import com.nachojerez.carpstrategy.domain.rules.RuleType
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import com.nachojerez.carpstrategy.domain.rules.TimeWindow
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import kotlin.math.roundToInt

@Composable
fun StrategyScreen(viewModel: StrategyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StrategyContent(state)
}

@Composable
fun StrategyContent(state: StrategyUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.strategy_title), style = MaterialTheme.typography.headlineSmall)
            Caption(stringResource(R.string.strategy_disclaimer))
        }
        when {
            state.isLoading -> item { Text(stringResource(R.string.strategy_loading)) }
            state.ruleIssues.isNotEmpty() -> {
                item { Text(stringResource(R.string.strategy_rules_invalid), color = MaterialTheme.colorScheme.error) }
                items(state.ruleIssues) { Text(ruleIssueText(it), style = MaterialTheme.typography.bodySmall) }
            }
            state.result == null -> item { Text(stringResource(R.string.strategy_no_data)) }
            else -> {
                val result = state.result
                item { SummaryCard(result) }
                if (!result.blocked) {
                    item { WindowsCard(state.windows) }
                    StrategyField.entries.forEach { field ->
                        val items = result.advice[field].orEmpty()
                        if (items.isNotEmpty()) {
                            item {
                                Section(stringResource(field.labelRes())) {
                                    items.forEach { Text(stringResource(R.string.strategy_advice_item, it.evidence.emoji(), it.text)) }
                                }
                            }
                        }
                    }
                }
                item { LevelsCard(result) }
                item {
                    Section(stringResource(R.string.strategy_why_title)) {
                        result.activeRules.forEach { RuleRow(it) }
                    }
                }
                if (result.notEvaluable.isNotEmpty()) {
                    item {
                        Section(stringResource(R.string.strategy_not_evaluable_title)) {
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

@Composable
private fun SummaryCard(result: StrategyResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (result.blocked) {
                Text(stringResource(R.string.strategy_blocked), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                result.blockingRules.forEach { Text(it.rule.description) }
                return@Column
            }
            val favorability = result.favorability
            val band = result.band
            if (favorability == null || band == null) {
                Text(stringResource(R.string.strategy_favorability_unknown))
            } else {
                val percent = (favorability * 100).roundToInt()
                Text(stringResource(R.string.strategy_favorability, stringResource(band.labelRes()), percent), style = MaterialTheme.typography.titleLarge)
                LinearProgressIndicator(progress = { favorability.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(
                    result.limitingLevel?.let { stringResource(R.string.strategy_limiting, stringResource(it.labelRes())) }
                        ?: stringResource(R.string.strategy_not_limited),
                )
            }
            result.demand?.let { Text(stringResource(R.string.strategy_demand, stringResource(it.labelRes()))) }
        }
    }
}

@Composable
private fun WindowsCard(windows: List<TimeWindow>) {
    Section(stringResource(R.string.strategy_windows_title)) {
        if (windows.isEmpty()) Text(stringResource(R.string.strategy_windows_none))
        windows.forEach { w ->
            Text(
                stringResource(
                    R.string.strategy_window_row,
                    w.evidence.emoji(),
                    stringResource(w.kind.labelRes()),
                    Formatting.clock(w.start),
                    Formatting.clock(w.end),
                ),
            )
        }
    }
}

@Composable
private fun LevelsCard(result: StrategyResult) {
    Section(stringResource(R.string.strategy_levels_title)) {
        result.levels.forEach { level ->
            Text(stringResource(R.string.strategy_level_row, stringResource(level.level.labelRes()), (level.value * 100).roundToInt()))
        }
    }
}

@Composable
private fun RuleRow(active: ActiveRule) {
    val rule = active.rule
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.strategy_rule_title, rule.evidence.emoji(), rule.description), style = MaterialTheme.typography.bodyMedium)
        active.checks.forEach { Caption(checkText(it)) }
        when {
            rule.type == RuleType.MEMBERSHIP && active.membershipInput != null -> Caption(
                stringResource(
                    R.string.strategy_rule_membership,
                    stringResource(rule.membership!!.parameter.labelRes()),
                    Formatting.number(active.membershipInput),
                    Formatting.number(active.appliedFactor, 2),
                ),
            )
            active.appliedFactor < 1.0 -> Caption(stringResource(R.string.strategy_rule_factor, Formatting.number(active.appliedFactor, 2)))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
