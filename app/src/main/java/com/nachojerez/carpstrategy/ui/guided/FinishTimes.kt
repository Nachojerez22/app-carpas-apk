package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.ui.diary.SessionForm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Terminar la sesión guiada con las horas que dices tú (fase 7.26): la sesión la cierras siempre
 * tú, y si se te olvidó, pones de qué hora a qué hora estuviste. Así las horas-caña salen bien.
 */

/** Lo que aparece en «Desde» y «Hasta» al terminar. [capped]: el fin propuesto es el del horario legal, no ahora. */
data class FinishDefaults(val start: String, val end: String, val capped: Boolean)

enum class FinishError { START, END, END_BEFORE_START, IN_FUTURE }

sealed interface FinishInput {
    data class Valid(val start: Instant, val end: Instant) : FinishInput

    data class Invalid(val error: FinishError) : FinishInput
}

/**
 * Inicio de la sesión y, como fin, ahora; si ya pasó el fin legal (te olvidaste de terminar), el
 * fin legal: después no se puede pescar.
 */
fun finishDefaults(session: Session, legalEnd: Instant?, now: Instant, zone: ZoneId): FinishDefaults {
    val capped = legalEnd != null && now.isAfter(legalEnd) && legalEnd.isAfter(session.start)
    val end = if (capped) legalEnd else now
    return FinishDefaults(start = CLOCK.format(session.start.atZone(zone)), end = CLOCK.format(end.atZone(zone)), capped = capped)
}

/** Las dos horas («07:15» o «7.15») son del día en que empezó la sesión; el fin, después del inicio y no en el futuro. */
fun parseFinishTimes(session: Session, startText: String, endText: String, now: Instant, zone: ZoneId): FinishInput {
    val day: LocalDate = session.start.atZone(zone).toLocalDate()
    val startTime = SessionForm.parseTime(startText) ?: return FinishInput.Invalid(FinishError.START)
    val endTime = SessionForm.parseTime(endText) ?: return FinishInput.Invalid(FinishError.END)
    val start = day.atTime(startTime).atZone(zone).toInstant()
    val end = day.atTime(endTime).atZone(zone).toInstant()
    if (!end.isAfter(start)) return FinishInput.Invalid(FinishError.END_BEFORE_START)
    if (end.isAfter(now.plusSeconds(FUTURE_TOLERANCE_S)) || start.isAfter(now)) return FinishInput.Invalid(FinishError.IN_FUTURE)
    return FinishInput.Valid(start, end)
}

/** La sesión sigue abierta después del fin legal o desde otro día: hay que terminarla poniendo las horas. */
fun isForgotten(session: Session, legalEnd: Instant?, now: Instant, zone: ZoneId): Boolean =
    (legalEnd != null && now.isAfter(legalEnd)) || session.start.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()

/** Se escribe la hora de ahora con minutos: un minuto de margen para que «Hasta» = ahora valga. */
private const val FUTURE_TOLERANCE_S = 60L

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
