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

    private fun env(minutes: Long) = GuidedEnv(at(minutes), inventory, madrid)

    private fun started(names: List<String> = listOf("fija"), groundbait: GroundbaitLevel? = null): Session {
        val s = GuidedSessions.start(summer, names, groundbait, env(0)).session
        return s.guided!!.rods.fold(s) { acc, rod -> GuidedSessions.accept(acc, rod.id, env(0)).session }
    }

    @Test
    fun `fase, fin legal y presion por fin de semana`() {
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
        val started = GuidedSessions.start(summer, listOf("fija"), null, env(0))
        assertEquals(StepKind.INITIAL, started.newProposals[1]?.kind)
        val accepted = GuidedSessions.accept(started.session, 1, env(1)).session
        assertEquals("Boilie fresa 20", accepted.bait)
        assertEquals("Pelo 25 lb", accepted.rig)
        assertNull(accepted.guided!!.rod(1)!!.log.pending)
        assertEquals(1, accepted.rods)
    }

    @Test
    fun `dos canas con nombre - cada una con su plan y su registro`() {
        val s = started(listOf("fija", "carrete"), GroundbaitLevel.HIGH)
        assertEquals(2, s.rods)
        assertEquals(listOf("fija", "carrete"), s.guided!!.rods.map { it.name })
        assertEquals("fija: Boilie fresa 20 · carrete: Boilie fresa 20", s.bait)
        var after = GuidedSessions.checkIn(s, 2, CheckIn(at(30), activity = HookActivity.CATCH), env(30)).session
        after = GuidedSessions.checkIn(after, 1, CheckIn(at(31), activity = HookActivity.CATCH, species = Species.SMALL), env(31)).session
        assertEquals(1, after.guided!!.rod(2)!!.log.current.catches)
        assertEquals(0, after.guided!!.rod(1)!!.log.current.catches)
        // Solo la carpa cuenta en la ficha (con su caña); el pequeño queda en el registro.
        assertEquals(listOf(2), after.catches.map { it.rod })
        assertEquals(1, after.bites)
        assertEquals(GroundbaitLevel.HIGH, after.guided!!.groundbaitOf(1))
        val rebait = GuidedSessions.checkIn(after, 1, CheckIn(at(40), rebait = GroundbaitLevel.LOW), env(40)).session
        assertEquals(GroundbaitLevel.LOW, rebait.guided!!.groundbaitOf(1))
        assertEquals(GroundbaitLevel.HIGH, rebait.guided!!.groundbaitOf(2))
    }

    @Test
    fun `nada en todas y cambio de viento`() {
        val s = started(listOf("fija", "carrete"))
        val nothing = GuidedSessions.nothingEverywhere(s, env(30)).session
        assertEquals(listOf(1, 1), nothing.guided!!.rods.map { it.log.allCheckIns.size })
        val wind = GuidedSessions.windChanged(nothing, env(35))
        assertEquals(listOf(at(35)), wind.session.guided!!.windChanges)
        assertTrue(wind.messages.values.all { GuidedMessage.WIND_CHANGED in it })
    }

    @Test
    fun `picada fallada y captura cuentan en la ficha`() {
        var s = started()
        s = GuidedSessions.checkIn(s, 1, CheckIn(at(30), activity = HookActivity.MISSED), env(30)).session
        s = GuidedSessions.checkIn(s, 1, CheckIn(at(45), activity = HookActivity.CATCH), env(45)).session
        s = GuidedSessions.checkIn(s, 1, CheckIn(at(60), activity = HookActivity.TOUCHES), env(60)).session
        assertEquals(2, s.bites)
        assertEquals(listOf(at(45)), s.catches.map { it.time })
    }

    @Test
    fun `dos no funciona en media hora proponen ya y rechazar no repite el paso`() {
        var s = started()
        s = GuidedSessions.checkIn(s, 1, CheckIn(at(10), notWorking = true), env(10)).session
        assertNull(s.guided!!.rod(1)!!.log.pending)
        val forced = GuidedSessions.checkIn(s, 1, CheckIn(at(20), notWorking = true), env(20))
        val first = forced.newProposals[1]!!
        assertTrue(first.forced)
        val rejected = GuidedSessions.reject(forced.session, 1, RejectReason.ALREADY_TRIED, "ya lo hice", env(21))
        assertTrue(rejected.newProposals[1] == null || rejected.newProposals[1]!!.kind != first.kind)
        assertEquals(Decision.REJECTED, rejected.session.guided!!.rod(1)!!.log.proposals.first { it.proposal == first }.decision)
    }

    @Test
    fun `terminar cierra los tramos y deja de proponer`() {
        val s = started(listOf("fija", "carrete"))
        val done = GuidedSessions.finish(s, at(300))
        assertEquals(at(300), done.end)
        assertTrue(done.guided!!.rods.all { it.log.current.end == at(300) })
        assertTrue(GuidedSessions.refresh(done, env(400)).newProposals.isEmpty())
        assertNull(GuidedSessions.active(listOf(done)))
        assertEquals(s, GuidedSessions.active(listOf(done, s)))
    }

    @Test
    fun `condiciones juntas o por separado, con su hora`() {
        var s = started(listOf("fija", "carrete"))
        s = GuidedSessions.conditionChanged(s, FieldCondition.LIGHT_RAIN, true, env(30)).session
        s = GuidedSessions.conditionChanged(s, FieldCondition.STORM, true, env(40)).session
        // Pasar a lluvia fuerte termina la ligera; la tormenta sigue.
        s = GuidedSessions.conditionChanged(s, FieldCondition.HEAVY_RAIN, true, env(45)).session
        val record = s.guided!!
        assertEquals(setOf(FieldCondition.HEAVY_RAIN, FieldCondition.STORM), record.activeConditions(at(50)))
        assertEquals(setOf(FieldCondition.LIGHT_RAIN), record.activeConditions(at(35)))
        s = GuidedSessions.conditionChanged(s, FieldCondition.STORM, false, env(70)).session
        assertEquals(setOf(FieldCondition.HEAVY_RAIN), s.guided!!.activeConditions(at(75)))
        // Marcar lo que ya está activo no duplica.
        val again = GuidedSessions.conditionChanged(s, FieldCondition.HEAVY_RAIN, true, env(80)).session
        assertEquals(s.guided!!.conditions.size, again.guided!!.conditions.size)
        assertTrue(GuidedSessions.context(s, 1, env(75)).conditions == setOf(FieldCondition.HEAVY_RAIN))
    }
}
