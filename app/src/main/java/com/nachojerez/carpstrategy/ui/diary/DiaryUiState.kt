package com.nachojerez.carpstrategy.ui.diary

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.JournalStats
import com.nachojerez.carpstrategy.domain.journal.JournalSummary
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionIssue
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.domain.manual.WeatherVariable
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.strategy.buildStrategyState
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Editor abierto: el formulario, la sesión original (null si es nueva) y los avisos. */
data class SessionEditor(
    val form: SessionForm,
    val original: Session?,
    val fieldErrors: Set<FormField> = emptySet(),
    val issues: List<SessionIssue> = emptyList(),
    /** True tras un primer intento de guardar con solo avisos: el segundo guarda. */
    val warningsAcknowledged: Boolean = false,
    /** Errores de las mediciones (agua, turbidez, nivel), con los textos de Datos. */
    val measurementIssues: List<ManualIssue> = emptyList(),
)

data class DiaryUiState(
    val isLoading: Boolean = true,
    val sessions: List<Session> = emptyList(),
    val ongoing: Session? = null,
    val month: YearMonth? = null,
    val monthSummary: JournalSummary = JournalSummary.EMPTY,
    val yearSummary: JournalSummary = JournalSummary.EMPTY,
    val byBand: Map<FavorabilityBand, JournalSummary> = emptyMap(),
    val recentCatchZones: Map<FishingZone, Instant> = emptyMap(),
    val editor: SessionEditor? = null,
    val now: Instant = Instant.EPOCH,
)

/** Estado de la pestaña Diario. Función pura. */
fun buildDiaryState(sessions: List<Session>, now: Instant, zone: ZoneId = Formatting.MADRID): DiaryUiState {
    val month = YearMonth.from(now.atZone(zone))
    val year = now.atZone(zone).year
    return DiaryUiState(
        isLoading = false,
        sessions = sessions.sortedByDescending { it.start },
        ongoing = sessions.filter { it.isOngoing }.maxByOrNull { it.start },
        month = month,
        monthSummary = JournalStats.summarizeMonth(sessions, month, zone),
        yearSummary = JournalStats.summarize(sessions.filter { it.start.atZone(zone).year == year }),
        byBand = JournalStats.byPredictedBand(sessions),
        recentCatchZones = JournalStats.recentCatchZones(sessions, now),
        now = now,
    )
}

/** Ventana legal del día en que empieza la sesión (vacía si no se puede calcular). */
fun legalWindowsFor(session: Session, zone: ZoneId = Formatting.MADRID): List<LegalWindow> {
    val date = session.start.atZone(zone).toLocalDate()
    return listOfNotNull(LegalWindow.of(SolarCalculator.sunTimes(date, session.location)))
}

/** Margen para considerar que una sesión "empieza ahora". */
val START_NOW_TOLERANCE: Duration = Duration.ofMinutes(15)

/** Solo se rellena el contexto si la caché meteorológica cubre el inicio (7 días atrás). */
val CONTEXT_MAX_AGE: Duration = Duration.ofDays(7)

/**
 * Completa una sesión antes de guardarla. Función pura.
 * - Valoración previa (solo al crearla, después es una copia fija): si empieza ahora, la que
 *   calcula la app en este momento; si se anota a posteriori, la última guardada en las 24 h
 *   anteriores al inicio. Nunca se calcula a posteriori: sería sesgo retrospectivo (§9).
 * - Contexto automático: derivados calculados para el inicio, si la caché lo cubre.
 */
fun completeSession(
    session: Session,
    isNew: Boolean,
    now: Instant,
    raw: RawWeather?,
    rules: RuleLoadResult?,
    snapshots: List<PredictionSnapshot>,
): Session {
    var result = session
    if (isNew && result.prediction == null) {
        val startsNow = !result.start.isBefore(now.minus(START_NOW_TOLERANCE))
        val fresh = if (startsNow && raw != null && rules != null) {
            val state = buildStrategyState(raw, rules, now, result.location)
            state.result?.let { JournalStats.snapshotOf(it, result.location, state.ruleContext, state.rulesFingerprint) }
        } else {
            null
        }
        result = result.copy(prediction = fresh ?: JournalStats.predictionBefore(snapshots, result.start, result.location))
    }
    val covered = !result.start.isAfter(now) && !result.start.isBefore(now.minus(CONTEXT_MAX_AGE))
    if (raw != null && rules != null && covered) {
        val state = buildStrategyState(raw, rules, result.start, result.location)
        state.derived?.let { derived ->
            val pressure = raw.merged.lastOrNull { !it.time.isAfter(result.start) }?.get(WeatherVariable.PRESSURE_MSL)?.value
            result = result.copy(context = JournalStats.contextOf(derived, pressure, state.ruleContext))
        }
    }
    return result
}
