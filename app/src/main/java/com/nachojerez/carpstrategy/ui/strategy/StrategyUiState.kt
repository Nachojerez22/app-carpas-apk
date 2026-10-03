package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.derived.DerivedCalculator
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.RuleContextBuilder
import com.nachojerez.carpstrategy.domain.rules.RuleEngine
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.SessionWindows
import com.nachojerez.carpstrategy.domain.rules.StrategyResult
import com.nachojerez.carpstrategy.domain.rules.TimeWindow
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.time.Duration
import java.time.Instant

data class StrategyUiState(
    val isLoading: Boolean = true,
    /** Errores de rules.json: si los hay, no se evalúa nada. */
    val ruleIssues: List<RuleIssue> = emptyList(),
    val result: StrategyResult? = null,
    val windows: List<TimeWindow> = emptyList(),
    val derived: DerivedConditions? = null,
    val regulationReviewed: String? = null,
)

/** Horas de previsión (próximas 24 h) en las que el viento de los modelos es incierto. */
private val UNCERTAIN_WIND_HORIZON: Duration = Duration.ofHours(24)

/** Evalúa la estrategia para [now]. Función pura. */
fun buildStrategyState(
    raw: RawWeather,
    rules: RuleLoadResult,
    now: Instant,
    location: GeoPoint = DefaultLocation.value.point,
): StrategyUiState {
    val ruleSet = when (rules) {
        is RuleLoadResult.Invalid -> return StrategyUiState(isLoading = false, ruleIssues = rules.issues)
        is RuleLoadResult.Loaded -> rules.ruleSet
    }
    if (raw.merged.isEmpty() && raw.manualRecords.isEmpty()) {
        return StrategyUiState(isLoading = false, regulationReviewed = ruleSet.regulationReviewed)
    }
    val derived = DerivedCalculator.compute(
        merged = raw.merged,
        ensemble = raw.ensemble,
        observations = raw.observations?.data?.observations.orEmpty(),
        stationAltitudeM = raw.observations?.data?.station?.altitudeM,
        gridElevationM = raw.forecast?.data?.elevationM,
        manual = raw.manualRecords,
        location = location,
        now = now,
        zone = Formatting.MADRID,
        manualEnabled = raw.priority.isEnabled(DataSource.MANUAL),
    )
    val horizon = now.plus(UNCERTAIN_WIND_HORIZON)
    val divergent = raw.ensemble.count { it.windDivergent && !it.time.isBefore(now) && !it.time.isAfter(horizon) }
    val context = RuleContextBuilder.build(derived, divergent, now, Formatting.MADRID)
    val result = RuleEngine.evaluate(ruleSet, context, now)
    return StrategyUiState(
        isLoading = false,
        result = result,
        windows = if (result.blocked) emptyList() else SessionWindows.suggest(derived.legalToday, derived.sunToday, derived.season?.season, derived.water?.trend3dC),
        derived = derived,
        regulationReviewed = ruleSet.regulationReviewed,
    )
}
