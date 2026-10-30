package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiExtras
import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.data.assistant.PlanInput
import com.nachojerez.carpstrategy.domain.advice.AdvisorInput
import com.nachojerez.carpstrategy.domain.advice.BaitUsage
import com.nachojerez.carpstrategy.domain.advice.DayPlan
import com.nachojerez.carpstrategy.domain.advice.DayPlans
import com.nachojerez.carpstrategy.domain.advice.Experience
import com.nachojerez.carpstrategy.domain.advice.ExperienceSummary
import com.nachojerez.carpstrategy.domain.advice.FishingAdvice
import com.nachojerez.carpstrategy.domain.advice.FishingAdvisor
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.assistant.AiContext
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.derived.DerivedCalculator
import com.nachojerez.carpstrategy.domain.derived.DerivedConditions
import com.nachojerez.carpstrategy.domain.derived.Freshness
import com.nachojerez.carpstrategy.domain.derived.FreshnessPolicy
import com.nachojerez.carpstrategy.domain.derived.SeasonByWater
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEngine
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.rodsToday
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.model.DefaultLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
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
    /** «Qué hago ahora»: cebo, puesto, distancia… (fase 7.5). */
    val advice: FishingAdvice? = null,
    /** Lecciones aceptadas (conocimiento aprendido). */
    val lessons: List<Lesson> = emptyList(),
    /** Lecciones propuestas por la IA pendientes de revisar. */
    val pendingLessons: Int = 0,
    /** Embalse donde vas a pescar (el más cercano a la ubicación) con su ficha. */
    val reservoir: ReservoirContext? = null,
    /** Puesto elegido en Estrategia para este embalse; null = automático (fase 7.10). */
    val chosenSpotId: Long? = null,
    /** Tus embalses (con puestos guardados, y cuántos) para el desplegable de Estrategia. */
    val myReservoirs: List<Pair<Reservoir, Int>> = emptyList(),
    /** Cañas que vas a pescar (paso ② de Estrategia, fase 7.12). */
    val rods: Int = DEFAULT_RODS,
    /** Lo que llevas hoy (fase 7.13): solo esto entra en el plan y en la IA. */
    val gear: List<GearItem> = emptyList(),
    /** Todo tu equipo, para marcar qué llevas hoy (paso ③). */
    val allGear: List<GearItem> = emptyList(),
    /** Tu cebado «normal» en kg por caña y sesión; null sin medida. */
    val groundbaitNormalKg: Double? = null,
    /** Cebos usados en el diario (para ordenar tus cebos guardados y ofrecer los que faltan). */
    val baitUsage: List<BaitUsage> = emptyList(),
)

data class PlanUiState(
    val configured: Boolean = false,
    val running: Boolean = false,
    val outcome: AiOutcome<AiPlan>? = null,
    /** [planKey] para el que se pidió (o se guardó) el plan; si no coincide, el plan ya no vale. */
    val key: String? = null,
    /** Plan preparado con «Preparar mañana» (fase 7.15) y su [planKey]: vale toda esa salida. */
    val prepared: AiOutcome<AiPlan>? = null,
    val preparedKey: String? = null,
)

/** Cañas por defecto si aún no has elegido. */
const val DEFAULT_RODS = 2

/** Cañas de Mi equipo que llevas hoy (fase 7.20); vacío = se elige el número de cañas. */
val StrategyUiState.rodSetups: List<RodSetup> get() = gear.rodsToday()

/** Número de cañas del plan: las de Mi equipo que llevas hoy o las elegidas por número. */
val StrategyUiState.planRods: Int get() = rodSetups.size.takeIf { it > 0 } ?: rods

/**
 * Para qué se pidió un plan de la IA (fase 7.12): día, embalse, puesto (o automático), cañas y
 * franja del día. Si cambia, el plan guardado ya no vale y se pide otro (una vez). Null si no
 * hay nada que planificar (sin datos o fuera del horario legal sin plan para el amanecer).
 */
fun planKey(state: StrategyUiState): String? {
    state.result?.takeIf { !it.blocked } ?: return null
    val advice = state.advice ?: return null
    val day = advice.forTime.atZone(Formatting.MADRID).toLocalDate()
    // Los cebos de hoy y tu medida también cuentan: si cambian, el plan (y el engodo) cambia.
    val baits = state.gear.filter { it.category == GearCategory.BAIT }.map { it.id }.sorted().joinToString(",")
    // Fase 7.20: las cañas de hoy (tipo y longitud) también: con otras cañas, otro plan.
    val rods = state.rodSetups.takeIf { it.isNotEmpty() }?.joinToString(",") { "${it.kind ?: "-"}${it.lengthM ?: ""}" } ?: state.rods
    return listOf(
        day, state.reservoir?.reservoir?.id ?: NO_RESERVOIR, state.chosenSpotId ?: "auto", rods, advice.dayPart, baits, state.groundbaitNormalKg ?: "-",
    ).joinToString("|")
}

/**
 * El plan del día para la pantalla: el de la app y, si el plan de la IA es válido y se pidió
 * para lo mismo ([planKey]), afinado por la IA. En automático, el puesto es el que recomienda la
 * IA si es uno de los tuyos; si no, el mejor de la app.
 */
fun dayPlanFor(state: StrategyUiState, plan: PlanUiState): DayPlan? {
    val advice = state.advice ?: return null
    val derived = state.derived ?: return null
    val ai = aiPlanFor(state, plan)?.value
    val aiSpot = ai?.bestSpot?.let { best -> advice.spots.firstOrNull { it.spot.name.trim().equals(best.spotName.trim(), ignoreCase = true) } }
    val spot = advice.chosen ?: aiSpot ?: advice.spots.firstOrNull()
    return DayPlans.build(advice, state.rods, phaseOf(derived), state.gear, ai, spot, state.groundbaitNormalKg, state.rodSetups)
}

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

/**
 * Lo que se manda a la IA para el plan antes de pescar (sin ubicación). Null si no hay
 * valoración o la sesión está bloqueada (fuera del horario legal). Función pura.
 */
fun planInput(state: StrategyUiState, rods: Int, gear: List<GearItem>, now: Instant): PlanInput? {
    val result = state.result?.takeIf { !it.blocked } ?: return null
    val derived = state.derived
    return PlanInput(
        now = now,
        zone = Formatting.MADRID,
        rods = rods.coerceIn(1, GuidedSessions.MAX_RODS),
        rodSetups = gear.rodsToday(),
        band = result.band?.name,
        limitingLevel = result.limitingLevel?.name,
        legalStart = derived?.legalToday?.start,
        legalEnd = derived?.legalToday?.end,
        windows = state.windows.map { Triple(it.start, it.end, it.kind.name) },
        advice = result.advice.flatMap { (field, items) -> items.map { Triple(field.name, it.text, it.evidence) } },
        waterC = derived?.water?.valueC,
        waterMeasured = derived?.water?.source == WaterTempSource.MEASURED,
        waterTrend3dC = derived?.water?.trend3dC,
        season = derived?.season?.season?.name,
        windFromDeg = derived?.wind24h?.dominantDirectionDeg,
        windKmh = derived?.wind24h?.meanSpeedKmh,
        rain24hMm = derived?.rain?.last24hMm,
        gear = gear,
        spots = state.spots,
        chosenSpot = state.advice?.chosen?.spot,
        groundbait = state.advice?.groundbait,
        groundbaitNormalKg = state.groundbaitNormalKg,
        extras = AiExtras(state.advice?.experience ?: ExperienceSummary.EMPTY, state.advice?.lessons ?: state.lessons, state.advice, state.reservoir),
    )
}

/** Contexto para validar el plan: cañas 1..n, tu equipo, tus puestos y el fin legal de hoy. */
fun planContext(state: StrategyUiState, rods: Int, gear: List<GearItem>, now: Instant): AiContext = AiContext(
    now = now,
    legalEnd = state.derived?.legalToday?.end,
    rodIds = (1..rods.coerceIn(1, GuidedSessions.MAX_RODS)).toSet(),
    gear = gear,
    spots = state.spots,
    legalStart = state.derived?.legalToday?.start,
    zone = Formatting.MADRID,
    groundbaitNormalKg = state.groundbaitNormalKg,
    rodSetups = gear.rodsToday().withIndex().associate { (i, r) -> i + 1 to r },
)

/** Estación de las reglas en el texto que usa la sesión guiada (`estacion` de rules.json). */
fun SeasonByWater.rulesText(): String = when (this) {
    SeasonByWater.WINTER -> "invierno"
    SeasonByWater.SPRING -> "primavera"
    SeasonByWater.SUMMER -> "verano"
    SeasonByWater.AUTUMN -> "otono"
}

/** Fase de pesca a partir de los derivados (agua y estación), como en la sesión guiada. */
fun phaseOf(derived: DerivedConditions): FishingPhase = GuidedEngine.phase(derived.water?.valueC, derived.season?.season?.rulesText())

/**
 * «Qué hago ahora» (fase 7.5): recomendación con tus sesiones en condiciones parecidas, tu
 * equipo, tus puestos y tus lecciones aceptadas. Null sin derivados. Función pura.
 */
fun adviceFor(
    derived: DerivedConditions?,
    now: Instant,
    sessions: List<Session>,
    gear: List<GearItem>,
    spots: List<Spot>,
    lessons: List<Lesson>,
    reservoir: ReservoirContext? = null,
    reservoirs: List<Reservoir> = emptyList(),
    chosenSpotId: Long? = null,
): FishingAdvice? {
    derived ?: return null
    val phase = phaseOf(derived)
    val water = derived.water?.valueC
    return FishingAdvisor.advise(
        AdvisorInput(
            now = now,
            phase = phase,
            waterC = water,
            sun = derived.sunToday,
            legal = derived.legalToday,
            windFromDeg = derived.wind24h?.dominantDirectionDeg,
            windKmh = derived.wind24h?.meanSpeedKmh,
            airC = derived.airTrend?.last24hMeanC,
            windPersistence = derived.wind24h?.persistence,
            gustKmh = derived.wind24h?.maxGustKmh,
            spots = spots,
            gear = gear,
            experience = Experience.forReservoir(sessions, reservoir?.reservoir, reservoirs, phase, water, Formatting.MADRID),
            lessons = lessons,
            reservoir = reservoir,
            chosenSpotId = chosenSpotId,
        ),
    )
}
