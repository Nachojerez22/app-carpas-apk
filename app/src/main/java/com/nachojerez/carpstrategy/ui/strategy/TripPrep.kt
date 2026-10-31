package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Preparar la salida (fase 7.15): con cobertura (la noche antes) se descarga la previsión, se pide el
 * plan de la IA para la mañana siguiente y se completan las fichas de cebos pendientes. En el
 * embalse, sin red, el plan preparado sigue valiendo toda la jornada.
 */

/** A esa hora ya estás pescando y las reglas valoran el amanecer, no la noche. 🟣 */
val PREP_AFTER_LEGAL_START: Duration = Duration.ofMinutes(30)

/** La próxima salida: hoy si aún no ha empezado el horario legal; si no, mañana. Null sin orto (no pasa en España). */
fun nextOuting(now: Instant, point: GeoPoint, zone: ZoneId): Instant? {
    val today = now.atZone(zone).toLocalDate()
    val todayStart = LegalWindow.of(SolarCalculator.sunTimes(today, point))?.start
    val day = if (todayStart != null && now.isBefore(todayStart)) today else today.plusDays(1)
    return LegalWindow.of(SolarCalculator.sunTimes(day, point))?.start?.plus(PREP_AFTER_LEGAL_START)
}

/** El [planKey] sin la franja del día: un plan preparado vale para toda la jornada. */
fun outingKey(planKey: String?): String? = planKey?.split("|")?.filterIndexed { i, _ -> i != DAY_PART_INDEX }?.joinToString("|")

/** [planKey] del plan preparado (ajuste local; el plan va en `GuidedSessionManager.KEY_PREPARED_PLAN`). */
const val KEY_PREPARED_KEY = "strategy_prepared_key"

/**
 * El plan preparado se copia en una sesión guiada solo si es de ese día y de ese embalse (fase
 * 7.17.1): si ese día pescas en otro, no vale para comparar después.
 */
fun preparedPlanFits(preparedKey: String?, reservoirId: String?, date: LocalDate): Boolean {
    val parts = preparedKey?.split("|") ?: return false
    return parts.getOrNull(0) == date.toString() && parts.getOrNull(1) == (reservoirId ?: NO_RESERVOIR)
}

/** Embalse en [planKey] cuando la ubicación no está en ninguno. */
const val NO_RESERVOIR = "sin_embalse"

/** Posición de la franja del día en [planKey]. */
private const val DAY_PART_INDEX = 4

/**
 * El plan de la IA que vale para lo que tienes elegido ahora: el pedido para esto mismo o, si no,
 * el preparado para esta salida (mismo día, embalse, puesto, cañas y cebos).
 */
fun aiPlanFor(state: StrategyUiState, plan: PlanUiState): AiOutcome<AiPlan>? {
    val key = planKey(state) ?: return null
    plan.outcome?.takeIf { plan.key == key }?.let { return it }
    return plan.prepared?.takeIf { plan.preparedKey != null && outingKey(plan.preparedKey) == outingKey(key) }
}

/**
 * Aviso «Mañana pinta bien» (fase 7.17): solo si la próxima salida sale favorable o muy favorable
 * (y legal), y una vez por salida.
 */
fun shouldNotifyOutlook(band: FavorabilityBand?, blocked: Boolean, outingDate: LocalDate, lastNotified: LocalDate?): Boolean =
    !blocked && (band == FavorabilityBand.FAVORABLE || band == FavorabilityBand.VERY_FAVORABLE) && lastNotified != outingDate

/** Una hora de la jornada evaluada con las reglas: su escala y si un filtro duro la impide. */
data class OutingHour(val at: Instant, val band: FavorabilityBand?, val blocked: Boolean)

/** La mejor franja de la jornada (fase 7.24): de [start] a [end], con la escala [band]. */
data class BestWindow(val start: Instant, val end: Instant, val band: FavorabilityBand)

/** Separación entre las horas que se evalúan. */
val OUTING_STEP: Duration = Duration.ofHours(1)

/**
 * Horas de la jornada legal de [outingStart] (la de [nextOuting]) que se evalúan, una cada
 * [OUTING_STEP], sin pasar de [PREP_AFTER_LEGAL_START] antes del fin legal.
 */
fun outingHours(outingStart: Instant, point: GeoPoint, zone: ZoneId): List<Instant> {
    val legal = LegalWindow.of(SolarCalculator.sunTimes(outingStart.atZone(zone).toLocalDate(), point)) ?: return listOf(outingStart)
    val last = legal.end.minus(PREP_AFTER_LEGAL_START)
    return generateSequence(outingStart) { it.plus(OUTING_STEP) }.takeWhile { !it.isAfter(last) }.toList()
}

/**
 * La mejor escala entre las horas sin filtro duro y su franja seguida más larga (la primera si
 * empatan). El fin es la última hora de la franja + [OUTING_STEP], sin pasar de [cap].
 */
fun bestWindow(hours: List<OutingHour>, cap: Instant): BestWindow? {
    val best = hours.filter { !it.blocked }.mapNotNull { it.band }.maxOrNull() ?: return null
    var run = emptyList<OutingHour>()
    var longest = emptyList<OutingHour>()
    hours.forEach { hour ->
        run = if (!hour.blocked && hour.band == best) run + hour else emptyList()
        if (run.size > longest.size) longest = run
    }
    val end = minOf(longest.last().at.plus(OUTING_STEP), cap)
    return BestWindow(longest.first().at, maxOf(end, longest.first().at), best)
}

/** Pasos de «Preparar mañana». */
enum class PrepStep { FORECAST, PLAN, SHEETS }

/** Cómo quedó el plan preparado. */
enum class PrepPlan { READY, FAILED, NO_KEY, NOTHING }

data class PrepResult(
    /** Hora de la salida para la que se preparó. */
    val target: Instant,
    val forecastOk: Boolean,
    val plan: PrepPlan,
    /** Qué dijo el proveedor si el plan falló (código y mensaje). */
    val planDetail: String? = null,
    val sheetsImproved: Int = 0,
    val sheetsPending: Int = 0,
)

data class PrepUiState(val step: PrepStep? = null, val result: PrepResult? = null) {
    val running: Boolean get() = step != null
}
