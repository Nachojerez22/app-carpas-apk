package com.nachojerez.carpstrategy.ui.strategy

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.rules.Condition
import com.nachojerez.carpstrategy.domain.rules.ConditionCheck
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleIssueCode
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import com.nachojerez.carpstrategy.domain.rules.RuleParameter
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.WindowKind
import com.nachojerez.carpstrategy.ui.conditions.Formatting

/** Etiqueta de evidencia (CONOCIMIENTO.md §2); la normativa se marca con ⚖. */
fun Evidence.emoji(): String = when (this) {
    Evidence.GREEN -> "🟢"
    Evidence.YELLOW -> "🟡"
    Evidence.RED -> "🔴"
    Evidence.PURPLE -> "🟣"
    Evidence.BLUE -> "🔵"
    Evidence.REGULATION -> "⚖"
}

@StringRes
fun FavorabilityBand.labelRes(): Int = when (this) {
    FavorabilityBand.VERY_UNFAVORABLE -> R.string.band_very_unfavorable
    FavorabilityBand.UNFAVORABLE -> R.string.band_unfavorable
    FavorabilityBand.INTERMEDIATE -> R.string.band_intermediate
    FavorabilityBand.FAVORABLE -> R.string.band_favorable
    FavorabilityBand.VERY_FAVORABLE -> R.string.band_very_favorable
}

@StringRes
fun FeedingDemand.labelRes(): Int = when (this) {
    FeedingDemand.VERY_LOW -> R.string.demand_very_low
    FeedingDemand.LOW -> R.string.demand_low
    FeedingDemand.MEDIUM -> R.string.demand_medium
    FeedingDemand.HIGH -> R.string.demand_high
    FeedingDemand.VERY_HIGH -> R.string.demand_very_high
    FeedingDemand.UNCERTAIN_HEAT -> R.string.demand_uncertain_heat
}

@StringRes
fun RuleLevel.labelRes(): Int = when (this) {
    RuleLevel.LEGALITY -> R.string.level_0
    RuleLevel.HABITAT -> R.string.level_1
    RuleLevel.TEMPERATURE -> R.string.level_2
    RuleLevel.PHYSICAL -> R.string.level_3
    RuleLevel.CATCHABILITY -> R.string.level_4
    RuleLevel.EXPLORATORY -> R.string.level_exploratory
}

/** Nombre del nivel sin número (el número va en el círculo de la cadena). */
@StringRes
fun RuleLevel.titleRes(): Int = when (this) {
    RuleLevel.LEGALITY -> R.string.level_title_0
    RuleLevel.HABITAT -> R.string.level_title_1
    RuleLevel.TEMPERATURE -> R.string.level_title_2
    RuleLevel.PHYSICAL -> R.string.level_title_3
    RuleLevel.CATCHABILITY -> R.string.level_title_4
    RuleLevel.EXPLORATORY -> R.string.level_exploratory
}

@StringRes
fun StrategyField.labelRes(): Int = when (this) {
    StrategyField.WHERE -> R.string.field_where
    StrategyField.WHEN -> R.string.field_when
    StrategyField.BAIT -> R.string.field_bait
    StrategyField.PRESENTATION -> R.string.field_presentation
    StrategyField.AVOID -> R.string.field_avoid
    StrategyField.NOTES -> R.string.field_notes
}

@StringRes
fun WindowKind.labelRes(): Int = when (this) {
    WindowKind.DAWN -> R.string.window_dawn
    WindowKind.MIDDAY -> R.string.window_midday
    WindowKind.AFTERNOON_DUSK -> R.string.window_afternoon_dusk
    WindowKind.DUSK -> R.string.window_dusk
}

@StringRes
fun RuleParameter.labelRes(): Int = when (this) {
    RuleParameter.WATER_TEMP -> R.string.param_temp_agua_c
    RuleParameter.WATER_MEASURED -> R.string.param_agua_medida
    RuleParameter.WATER_TREND_3D -> R.string.param_tendencia_agua_3d_c
    RuleParameter.SEASON -> R.string.param_estacion
    RuleParameter.AIR_TEMP_24H -> R.string.param_temp_aire_24h_c
    RuleParameter.AIR_TREND -> R.string.param_tendencia_aire_c
    RuleParameter.HOT_DAYS -> R.string.param_dias_calor
    RuleParameter.COLD_DAYS -> R.string.param_dias_frio
    RuleParameter.WIND_24H -> R.string.param_viento_24h_kmh
    RuleParameter.WIND_PERSISTENCE_24H -> R.string.param_viento_persistencia_24h
    RuleParameter.GUST_MAX_24H -> R.string.param_racha_max_24h_kmh
    RuleParameter.UNCERTAIN_WIND_HOURS -> R.string.param_horas_viento_incierto
    RuleParameter.RAIN_24H -> R.string.param_lluvia_24h_mm
    RuleParameter.RAIN_72H -> R.string.param_lluvia_72h_mm
    RuleParameter.RUNOFF -> R.string.param_escorrentia
    RuleParameter.LEVEL_DELTA_7D -> R.string.param_nivel_delta_7d_hm3
    RuleParameter.LEVEL_PERCENT -> R.string.param_nivel_pct
    RuleParameter.PRESSURE_DELTA_24H -> R.string.param_presion_delta_24h_hpa
    RuleParameter.CLOUD_COVER -> R.string.param_nubosidad_pct
    RuleParameter.MOON_ILLUMINATION -> R.string.param_luna_iluminacion
    RuleParameter.LEGAL_WINDOW_AVAILABLE -> R.string.param_ventana_legal_disponible
    RuleParameter.IN_LEGAL_HOURS -> R.string.param_en_horario_legal
    RuleParameter.WEEKEND -> R.string.param_fin_de_semana
    RuleParameter.MONTH -> R.string.param_mes
}

@Composable
private fun bool(value: Boolean?): String = when (value) {
    true -> stringResource(R.string.yes)
    false -> stringResource(R.string.no)
    null -> Formatting.number(null)
}

/** "temp. agua (°C) = 18,4 (17 a 21)" */
@Composable
fun checkText(check: ConditionCheck): String {
    val c = check.condition
    val actual = when (val v = check.actual) {
        is Double -> Formatting.number(v, 2).trimEnd('0').trimEnd(',')
        is Boolean -> bool(v)
        is String -> v
        else -> Formatting.number(null)
    }
    val expected = when (c) {
        is Condition.Range -> when {
            c.min != null && c.max != null -> stringResource(R.string.cond_range_both, Formatting.number(c.min, 1), Formatting.number(c.max, 1))
            c.min != null -> stringResource(R.string.cond_range_min, Formatting.number(c.min, 1))
            else -> stringResource(R.string.cond_range_max, Formatting.number(c.max, 1))
        }
        is Condition.Is -> stringResource(R.string.cond_is, bool(c.value))
        is Condition.OneOf -> stringResource(R.string.cond_one_of, c.values.joinToString(", "))
    }
    return stringResource(R.string.strategy_check, stringResource(c.parameter.labelRes()), actual, expected)
}

@Composable
fun ruleIssueText(issue: RuleIssue): String {
    val a = issue.args
    fun arg(i: Int) = a.getOrElse(i) { "?" }
    val path = issue.path ?: "?"
    val message = when (issue.code) {
        RuleIssueCode.NOT_JSON -> stringResource(R.string.rule_issue_not_json)
        RuleIssueCode.BAD_FORMAT -> stringResource(R.string.rule_issue_bad_format, arg(0))
        RuleIssueCode.UNSUPPORTED_VERSION -> stringResource(R.string.rule_issue_unsupported_version, arg(0))
        RuleIssueCode.NO_RULES -> stringResource(R.string.rule_issue_no_rules)
        RuleIssueCode.RULE_NOT_OBJECT -> stringResource(R.string.rule_issue_not_object)
        RuleIssueCode.MISSING_FIELD -> stringResource(R.string.rule_issue_missing_field, path)
        RuleIssueCode.INVALID_ID -> stringResource(R.string.rule_issue_invalid_id, arg(0))
        RuleIssueCode.DUPLICATE_ID -> stringResource(R.string.rule_issue_duplicate_id, arg(0))
        RuleIssueCode.INVALID_VALUE -> stringResource(R.string.rule_issue_invalid_value, path, arg(0), arg(1))
        RuleIssueCode.UNKNOWN_PARAMETER -> stringResource(R.string.rule_issue_unknown_parameter, path)
        RuleIssueCode.WRONG_PARAMETER_TYPE -> stringResource(R.string.rule_issue_wrong_parameter_type, path, arg(0))
        RuleIssueCode.MIN_GREATER_THAN_MAX -> stringResource(R.string.rule_issue_min_greater_than_max, path, arg(0), arg(1))
        RuleIssueCode.EMPTY_CONDITION -> stringResource(R.string.rule_issue_empty_condition, path)
        RuleIssueCode.FACTOR_OUT_OF_RANGE -> stringResource(R.string.rule_issue_factor_out_of_range, arg(0))
        RuleIssueCode.INVALID_MEMBERSHIP -> stringResource(R.string.rule_issue_invalid_membership, path)
        RuleIssueCode.RED_WITH_WEIGHT -> stringResource(R.string.rule_issue_red_with_weight)
        RuleIssueCode.EXPLORATORY_WITH_WEIGHT -> stringResource(R.string.rule_issue_exploratory_with_weight)
        RuleIssueCode.HARD_FILTER_NOT_LEVEL_0 -> stringResource(R.string.rule_issue_hard_filter_not_level_0)
        RuleIssueCode.UNKNOWN_PLACEHOLDER -> stringResource(R.string.rule_issue_unknown_placeholder, path, arg(0))
    }
    val prefix = issue.ruleNumber?.let { stringResource(R.string.rule_issue_prefix_rule, it, issue.ruleId ?: "?") }
        ?: stringResource(R.string.rule_issue_prefix_file)
    return stringResource(R.string.rule_issue_line, prefix, message)
}
