package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.journal.FeatureSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedSessionsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    // Miércoles 15 de julio de 2026, 7:00 en Madrid.
    private val t0 = Instant.parse("2026-07-15T05:00:00Z")
    private val point = GeoPoint(38.2, -6.75)
    private val boilie = GearItem("2", GearCategory.BAIT, "Boilie fresa 20", baitType = BaitType.BOILIE)
    private val hair = GearItem("5", GearCategory.RIG, "Pelo 25 lb", rigType = RigType.HAIR_BOTTOM)
    private val inventory = listOf(boilie, hair)

    private val summer = Session(
        start = t0,
        location = point,
        createdAt = t0,
        context = SessionContext(features = FeatureSnapshot(numbers = mapOf("temp_agua_c" to 24.0), texts = mapOf("estacion" to "verano"))),
    )

    private fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

    private fun ctx(session: Session, minutes: Long) = GuidedSessions.context(session, at(minutes), inventory, madrid)

    @Test
    fun `fase, fin legal y presión por fin de semana`() {
        assertEquals(FishingPhase.SUMMER, GuidedSessions.phaseOf(summer))
        assertEquals(FishingPhase.SPRING, GuidedSessions.phaseOf(summer.copy(context = null)))
        val end = GuidedSessions.legalEnd(summer, madrid)!!
        // Ocaso de mediados de julio en Brovales ≈ 21:50 hora local → fin legal ≈ 22:50.
        assertEquals(22, end.atZone(madrid).hour)
        assertFalse(GuidedSessions.highPressure(summer, t0, madrid))
        assertTrue(GuidedSessions.highPressure(summer, Instant.parse("2026-07-18T08:00:00Z"), madrid))
        assertTrue(GuidedSessions.highPressure(summer.copy(otherAnglers = 1), t0, madrid))
    }

    @Test
    fun `aceptar el plan A pasa cebo y montaje a la ficha`() {
        val started = GuidedSessions.start(summer, ctx(summer, 0))
        assertEquals(StepKind.INITIAL, started.newProposal?.kind)
        val accepted = GuidedSessions.accept(started.session, ctx(started.session, 1)).session
        assertEquals("Boilie fresa 20", accepted.bait)
        assertEquals("Pelo 25 lb", accepted.rig)
        assertNull(accepted.guided!!.pending)
        assertEquals(1, accepted.guided!!.segments.size)
    }

    @Test
    fun `picada fallada y captura cuentan en la ficha`() {
        var s = GuidedSessions.accept(GuidedSessions.start(summer, ctx(summer, 0)).session, ctx(summer, 0)).session
        s = GuidedSessions.checkIn(s, CheckIn(at(30), activity = HookActivity.MISSED), ctx(s, 30)).session
        s = GuidedSessions.checkIn(s, CheckIn(at(45), activity = HookActivity.CATCH), ctx(s, 45)).session
        s = GuidedSessions.checkIn(s, CheckIn(at(60), activity = HookActivity.TOUCHES), ctx(s, 60)).session
        assertEquals(2, s.bites)
        assertEquals(listOf(at(45)), s.catches.map { it.time })
    }

    @Test
    fun `dos no funciona en media hora proponen ya y rechazar no repite el paso`() {
        var s = GuidedSessions.accept(GuidedSessions.start(summer, ctx(summer, 0)).session, ctx(summer, 0)).session
        s = GuidedSessions.checkIn(s, CheckIn(at(10), notWorking = true), ctx(s, 10)).session
        assertNull(s.guided!!.pending)
        val forced = GuidedSessions.checkIn(s, CheckIn(at(20), notWorking = true), ctx(s, 20))
        val first = forced.newProposal!!
        assertTrue(first.forced)
        val rejected = GuidedSessions.reject(forced.session, RejectReason.ALREADY_TRIED, "ya lo hice", ctx(forced.session, 21))
        // Tras rechazar, no se vuelve a proponer lo mismo de inmediato.
        assertTrue(rejected.newProposal == null || rejected.newProposal!!.kind != first.kind)
        assertEquals(Decision.REJECTED, rejected.session.guided!!.proposals.first { it.proposal == first }.decision)
    }

    @Test
    fun `terminar cierra el tramo y deja de proponer`() {
        val s = GuidedSessions.accept(GuidedSessions.start(summer, ctx(summer, 0)).session, ctx(summer, 0)).session
        val done = GuidedSessions.finish(s, at(300))
        assertEquals(at(300), done.end)
        assertEquals(at(300), done.guided!!.current.end)
        assertNull(GuidedSessions.refresh(done, ctx(done, 400)).newProposal)
        assertNull(GuidedSessions.active(listOf(done)))
        assertEquals(s, GuidedSessions.active(listOf(done, s)))
    }
}
