package com.nachojerez.carpstrategy.domain.journal

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import java.time.Duration
import java.time.Instant

enum class SessionIssueCode(val isError: Boolean) {
    END_BEFORE_START(true),
    TOO_LONG(true),
    START_IN_FUTURE(true),
    INVALID_RODS(true),
    NEGATIVE_COUNT(true),
    /** Sesión terminada sin capturas: hay que decir si fue bolo (§9: bolos explícitos). */
    BLANK_NOT_ANSWERED(true),
    BLANK_WITH_CATCHES(true),
    INVALID_WEIGHT(true),
    INVALID_RANGE(true),
    /** ⚖ Normativa: máximo 3 cañas (CONOCIMIENTO.md §4.1). Aviso, no bloquea el registro. */
    RODS_OVER_LIMIT(false),
    /** ⚖ Parte de la sesión fuera del horario legal. Aviso, no bloquea el registro. */
    OUTSIDE_LEGAL_HOURS(false),
    CATCH_OUTSIDE_SESSION(false),
}

data class SessionIssue(val code: SessionIssueCode, val args: List<String> = emptyList())

/** Validación de una sesión antes de guardarla. Función pura. */
object SessionValidator {
    const val MAX_LEGAL_RODS = 3
    const val MAX_RODS = 10
    val MAX_DURATION: Duration = Duration.ofHours(24)
    const val MAX_WEIGHT_KG = 60.0

    /**
     * [legal] son las ventanas legales de los días que toca la sesión (normalmente una). Si
     * está vacía no se comprueba el horario (sin dato no se avisa en falso).
     */
    fun validate(session: Session, now: Instant, legal: List<LegalWindow>): List<SessionIssue> = buildList {
        val end = session.end
        if (session.start.isAfter(now.plus(Duration.ofMinutes(5)))) add(SessionIssue(SessionIssueCode.START_IN_FUTURE))
        if (end != null && !end.isAfter(session.start)) add(SessionIssue(SessionIssueCode.END_BEFORE_START))
        if (end != null && Duration.between(session.start, end) > MAX_DURATION) add(SessionIssue(SessionIssueCode.TOO_LONG))
        if (session.rods !in 1..MAX_RODS) {
            add(SessionIssue(SessionIssueCode.INVALID_RODS, listOf("$MAX_RODS")))
        } else if (session.rods > MAX_LEGAL_RODS) {
            add(SessionIssue(SessionIssueCode.RODS_OVER_LIMIT, listOf("$MAX_LEGAL_RODS")))
        }
        if (session.bites < 0 || session.losses < 0 || (session.otherAnglers ?: 0) < 0) add(SessionIssue(SessionIssueCode.NEGATIVE_COUNT))
        if (session.rodHoursOverride != null && session.rodHoursOverride !in 0.0..(MAX_RODS * 24.0)) add(SessionIssue(SessionIssueCode.INVALID_RANGE))
        if ((session.depthM ?: 0.0) !in 0.0..100.0 || (session.groundbaitKg ?: 0.0) !in 0.0..100.0) add(SessionIssue(SessionIssueCode.INVALID_RANGE))
        if (session.catches.any { (it.weightKg ?: 1.0) !in 0.0..MAX_WEIGHT_KG || it.weightKg == 0.0 }) add(SessionIssue(SessionIssueCode.INVALID_WEIGHT))
        if (session.catches.isNotEmpty() && session.blank == true) add(SessionIssue(SessionIssueCode.BLANK_WITH_CATCHES))
        if (end != null && session.catches.isEmpty() && session.blank == null) add(SessionIssue(SessionIssueCode.BLANK_NOT_ANSWERED))
        val catchOutside = session.catches.any { c ->
            val t = c.time ?: return@any false
            t.isBefore(session.start) || (end != null && t.isAfter(end))
        }
        if (catchOutside) add(SessionIssue(SessionIssueCode.CATCH_OUTSIDE_SESSION))
        if (legal.isNotEmpty() && !isInsideLegal(session.start, end ?: now, legal)) add(SessionIssue(SessionIssueCode.OUTSIDE_LEGAL_HOURS))
    }

    /** True si todo el intervalo cabe en alguna de las ventanas legales. */
    fun isInsideLegal(start: Instant, end: Instant, legal: List<LegalWindow>): Boolean =
        legal.any { start in it && end in it }
}
