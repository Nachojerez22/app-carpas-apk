package com.nachojerez.carpstrategy.ui.guided

import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FinishTimesTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    // Sábado 31 de octubre de 2026, 08:10 en Madrid (UTC+1).
    private val start = Instant.parse("2026-10-31T07:10:00Z")
    private val legalEnd = Instant.parse("2026-10-31T19:20:00Z")
    private val session = Session(start = start, location = GeoPoint(38.35, -6.7), createdAt = start)

    @Test
    fun `durante la sesion se propone de la hora de inicio a ahora`() {
        val now = Instant.parse("2026-10-31T12:45:00Z")
        assertEquals(FinishDefaults("08:10", "13:45", capped = false), finishDefaults(session, legalEnd, now, madrid))
        assertFalse(isForgotten(session, legalEnd, now, madrid))
    }

    @Test
    fun `si te olvidaste de terminar se propone el fin legal`() {
        val nextMorning = Instant.parse("2026-11-01T09:00:00Z")
        assertEquals(FinishDefaults("08:10", "20:20", capped = true), finishDefaults(session, legalEnd, nextMorning, madrid))
        assertTrue(isForgotten(session, legalEnd, nextMorning, madrid))
        assertTrue(isForgotten(session, null, nextMorning, madrid))
    }

    @Test
    fun `las horas son del dia en que empezo la sesion`() {
        val nextMorning = Instant.parse("2026-11-01T09:00:00Z")
        val input = parseFinishTimes(session, "8:30", "18.15", nextMorning, madrid)
        assertEquals(FinishInput.Valid(Instant.parse("2026-10-31T07:30:00Z"), Instant.parse("2026-10-31T17:15:00Z")), input)
    }

    @Test
    fun `horas que no valen`() {
        val now = Instant.parse("2026-10-31T12:45:00Z")
        assertEquals(FinishInput.Invalid(FinishError.START), parseFinishTimes(session, "ocho", "12:00", now, madrid))
        assertEquals(FinishInput.Invalid(FinishError.END), parseFinishTimes(session, "08:10", "25:00", now, madrid))
        assertEquals(FinishInput.Invalid(FinishError.END_BEFORE_START), parseFinishTimes(session, "10:00", "09:00", now, madrid))
        assertEquals(FinishInput.Invalid(FinishError.IN_FUTURE), parseFinishTimes(session, "08:10", "15:00", now, madrid))
        // «Hasta» = ahora vale aunque se escriba sin segundos.
        assertTrue(parseFinishTimes(session, "08:10", "13:45", now, madrid) is FinishInput.Valid)
    }
}
