package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.derived.DerivedCalculator
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.Freshness
import com.nachojerez.carpstrategy.domain.derived.FreshnessPolicy
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.RuleContext
import com.nachojerez.carpstrategy.domain.rules.RuleContextBuilder
import com.nachojerez.carpstrategy.domain.rules.RuleEngine
import com.nachojerez.carpstrategy.domain.rules.RuleIssue
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
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
    /** Antigüedad de la previsión usada; con datos antiguos se avisa en la pantalla. */
    val forecastFreshness: Freshness? = null,
    /** 🟢 Zonas con capturas propias recientes: sugerir rotar puesto y montaje (§5.8). */
    val recentCatchZones: Map<FishingZone, Instant> = emptyMap(),
    /** Parámetros con los que se evaluaron las reglas (se guardan con la valoración, §10). */
    val ruleContext: RuleContext? = null,
    val rulesFingerprint: String? = null,
    /** Mis puestos, para ver cómo les da el viento (desempate, §5.4 🟡). */
    val spots: List<Spot> = emptyList(),
)

/**
 * Próxima ventana sugerida (la que está en curso o la siguiente que empieza); si ya han pasado
 * todas, null. Las ventanas ya vienen recortadas al horario legal.
 */
fun nextWindow(windows: List<TimeWindow>, now: Instant): TimeWindow? =
    windows.filter { it.end.isAfter(now) }.minByOrNull { it.start }

/** Horas de previsión (próximas 24 h) en las que el viento de los modelos es incierto. */
private val UNCERTAIN_WIND_HORIZON: Duration = Duration.ofHours(24)

/** Evalúa la estrategia para [now]. Función pura. */
fun buildStrategyState(
    raw: RawWeather,
    rules: RuleLoadResult,
    now: Instant,
    location: GeoPoint = DefaultLocation.value.point,
    sessions: List<Session> = emptyList(),
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
        forecastFreshness = raw.forecast?.let { FreshnessPolicy.evaluate(it.fetchedAt, now) },
        recentCatchZones = JournalStats.recentCatchZones(sessions, now),
        ruleContext = context,
        rulesFingerprint = ruleSet.fingerprint,
    )
}

/** Estado de un nivel de la cadena de filtros, tal y como se muestra en la pantalla. */
enum class LevelStatus { PASSED, LIMITS, REDUCES, NEUTRAL, BLOCKS }

/** Una fila de la cadena 0 → 4 con el porqué (descripción de la regla que más resta). */
data class ChainRow(val level: RuleLevel, val status: LevelStatus, val why: String?)

/**
 * Cadena de filtros para la pantalla. Nivel 0: bloquea si hay un filtro duro activo. Niveles
 * 1–4: «limita» el nivel más bajo (el que fija la valoración), «resta» los demás por debajo
 * de 1, «superado» si hay reglas que se cumplen sin restar y «neutro» si no aplica ninguna.
 */
fun chainRows(result: StrategyResult): List<ChainRow> {
    val legality = ChainRow(
        level = RuleLevel.LEGALITY,
        status = if (result.blocked) LevelStatus.BLOCKS else LevelStatus.PASSED,
        why = result.blockingRules.firstOrNull()?.rule?.description,
    )
    val chained = result.levels.map { level ->
        val reducing = level.rules.filter { it.appliedFactor < 1.0 }.minByOrNull { it.appliedFactor }
        val status = when {
            result.blocked -> LevelStatus.NEUTRAL
            level.level == result.limitingLevel -> LevelStatus.LIMITS
            level.value < 1.0 -> LevelStatus.REDUCES
            level.rules.isEmpty() -> LevelStatus.NEUTRAL
            else -> LevelStatus.PASSED
        }
        ChainRow(level.level, status, reducing?.rule?.description)
    }
    return listOf(legality) + chained
}
