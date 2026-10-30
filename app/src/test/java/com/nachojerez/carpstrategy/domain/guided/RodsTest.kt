package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RodsTest {
    private val t0 = Instant.parse("2026-07-15T05:00:00Z")
    private val legalEnd = Instant.parse("2026-07-15T21:00:00Z")
    private val maize = GearItem("1", GearCategory.BAIT, "Maíz dulce", baitType = BaitType.MAIZE)
    private val boilie = GearItem("2", GearCategory.BAIT, "Boilie fresa 20", baitType = BaitType.BOILIE)
    private val popup = GearItem("3", GearCategory.BAIT, "Pop-up piña", baitType = BaitType.POPUP)
    private val tiger = GearItem("4", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT)
    private val inventory = listOf(boilie, popup, maize, tiger)

    private fun ctx(phase: FishingPhase, kind: RodKind?, minutes: Long = 0, inv: List<GearItem> = inventory) =
        GuidedContext(phase, t0.plus(Duration.ofMinutes(minutes)), legalEnd, inv, rodKind = kind)

    @Test
    fun `reglas de cada tipo de cana`() {
        assertEquals(setOf(Column.BOTTOM, Column.ZIG, Column.SURFACE), RodRules.columns(RodKind.FIXED))
        assertEquals(setOf(Column.BOTTOM, Column.POPUP), RodRules.columns(RodKind.CARP))
        assertEquals(Column.entries.toSet(), RodRules.columns(null))
        assertFalse(RodRules.baitAllowed(RodKind.FIXED, BaitType.BOILIE))
        assertFalse(RodRules.baitAllowed(RodKind.FIXED, BaitType.POPUP))
        assertTrue(RodRules.baitAllowed(RodKind.FIXED, BaitType.MAIZE))
        assertTrue(RodRules.baitAllowed(RodKind.CARP, BaitType.MAIZE))
        assertTrue(RodRules.baitAllowed(RodKind.CARP, BaitType.BOILIE))
        assertEquals(0.0..9.0, RodRules.distanceRange(RodKind.FIXED, 9))
        assertEquals(7.0..100.0, RodRules.distanceRange(RodKind.CARP, null))
        assertEquals(3..4, RodRules.weightRange(RodKind.FIXED))
        assertEquals(40..90, RodRules.weightRange(RodKind.CARP))
        assertNull(RodRules.weightRange(null))
        assertEquals(5.0, RodRules.clampDistance(RodSetup("", RodKind.FIXED, 5), 60.0))
        assertEquals(7.0, RodRules.clampDistance(RodSetup("", RodKind.CARP), 3.0))
        assertEquals(150.0, RodRules.clampDistance(null, 150.0))
    }

    @Test
    fun `canas de hoy desde el equipo y, sin canas, por numero`() {
        val gear = listOf(
            maize,
            GearItem("r1", GearCategory.ROD, "Larga", rodKind = RodKind.FIXED, rodLengthM = 9),
            GearItem("r2", GearCategory.ROD, "Carrete", rodKind = RodKind.CARP, rodLengthM = 9),
            GearItem("r3", GearCategory.ROD, "Corta", rodKind = RodKind.FIXED, rodLengthM = 5, available = false),
        )
        assertEquals(listOf(RodSetup("Larga", RodKind.FIXED, 9), RodSetup("Carrete", RodKind.CARP, null)), gear.rodsToday())
        assertEquals(gear.rodsToday(), rodsFor(2, gear))
        assertEquals(List(2) { RodSetup("") }, rodsFor(2, listOf(maize)))
    }

    @Test
    fun `la cana de carpfishing siempre entra aunque lleves mas fijas que el maximo`() {
        val fixed = listOf(9, 8, 7, 5).map { GearItem("f$it", GearCategory.ROD, "Fija $it", rodKind = RodKind.FIXED, rodLengthM = it) }
        val carp = GearItem("c", GearCategory.ROD, "Carrete", rodKind = RodKind.CARP)
        assertEquals(
            listOf(RodSetup("Fija 9", RodKind.FIXED, 9), RodSetup("Fija 8", RodKind.FIXED, 8), RodSetup("Carrete", RodKind.CARP, null)),
            (fixed + carp).rodsToday(),
        )
        // Sin carpfishing, las tres primeras fijas; con una de carpfishing ya entre las tres, no cambia nada.
        assertEquals(fixed.take(3).map { it.toRodSetup() }, fixed.rodsToday())
        assertEquals(listOf("Carrete", "Fija 9", "Fija 8"), (listOf(carp) + fixed).rodsToday().map { it.name })
    }

    @Test
    fun `la fija nunca empieza con boilies y la de carpfishing puede`() {
        val fixed = GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, RodKind.FIXED))
        assertEquals(BaitType.MAIZE, fixed.bait)
        assertEquals(Column.BOTTOM, fixed.column)
        val carp = GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, RodKind.CARP))
        assertEquals(BaitType.BOILIE, carp.bait)
        // Sin cebos en el equipo, la fija no propone boilie de partida.
        assertTrue(RodRules.baitAllowed(RodKind.FIXED, GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, RodKind.FIXED, inv = emptyList())).bait))
    }

    @Test
    fun `columnas de la escalera segun el tipo de cana`() {
        assertEquals(Column.POPUP, GuidedEngine.nextColumn(Column.BOTTOM, FishingPhase.SUMMER, RodKind.CARP))
        assertEquals(Column.BOTTOM, GuidedEngine.nextColumn(Column.POPUP, FishingPhase.SUMMER, RodKind.CARP))
        assertEquals(Column.ZIG, GuidedEngine.nextColumn(Column.BOTTOM, FishingPhase.SUMMER, RodKind.FIXED))
        assertEquals(Column.SURFACE, GuidedEngine.nextColumn(Column.ZIG, FishingPhase.HEAT, RodKind.FIXED))
        assertNull(GuidedEngine.nextColumn(Column.BOTTOM, FishingPhase.WINTER, RodKind.FIXED))
        assertEquals(Column.POPUP, GuidedEngine.nextColumn(Column.BOTTOM, FishingPhase.SUMMER))
    }

    @Test
    fun `la fija en invierno no tiene peldano de columna y la de carpfishing nunca sube a media agua`() {
        // Invierno: la escalera empieza por distancia y sigue por columna; la fija se la salta.
        val fixedLog = GuidedLog.start(t0, GuidedEngine.initialProposal(ctx(FishingPhase.WINTER, RodKind.FIXED))).accept(t0)
            .withProposal(Proposal(StepKind.DISTANCE, Situation.NO_SIGNALS, createdAt = t0.plus(Duration.ofMinutes(150)), evidence = Evidence.PURPLE))
            .accept(t0.plus(Duration.ofMinutes(151)))
        val next = GuidedEngine.evaluate(fixedLog, ctx(FishingPhase.WINTER, RodKind.FIXED, minutes = 151 + 150)).proposal
        assertTrue(next == null || next.kind != StepKind.COLUMN)

        // Verano: la de carpfishing sube como mucho a pop-up.
        val carpLog = GuidedLog.start(t0, GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, RodKind.CARP))).accept(t0)
        val p = GuidedEngine.evaluate(carpLog, ctx(FishingPhase.SUMMER, RodKind.CARP, minutes = 61)).proposal!!
        assertEquals(StepKind.COLUMN, p.kind)
        assertEquals(Column.POPUP, p.column)
    }

    @Test
    fun `contra el cangrejo la fija cambia el cebo a fondo, sin pop-up`() {
        val log = GuidedLog.start(t0, GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, RodKind.FIXED))).accept(t0)
            .withCheckIn(CheckIn(t0.plus(Duration.ofMinutes(30)), baitState = BaitState.GONE))
            .withCheckIn(CheckIn(t0.plus(Duration.ofMinutes(60)), baitState = BaitState.GONE))
        val p = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, RodKind.FIXED, minutes = 60)).proposal!!
        assertEquals(StepKind.ANTI_CRAB, p.kind)
        assertNull(p.column)
        assertTrue(RodRules.baitAllowed(RodKind.FIXED, p.bait))
    }

    @Test
    fun `la sesion guiada guarda el tipo de cada cana`() {
        val session = Session(
            start = t0, location = GeoPoint(38.35, -6.70), createdAt = t0,
        )
        val env = GuidedEnv(t0, inventory, ZoneId.of("Europe/Madrid"))
        val setups = listOf(RodSetup("Larga", RodKind.FIXED, 9), RodSetup("Carrete", RodKind.CARP))
        val started = GuidedSessions.start(session, setups.map { it.name }, null, env, setups = setups).session
        val rods = started.guided!!.rods
        assertEquals(listOf(RodKind.FIXED, RodKind.CARP), rods.map { it.kind })
        assertEquals(9, rods[0].lengthM)
        assertTrue(RodRules.baitAllowed(RodKind.FIXED, rods[0].log.pending!!.proposal.bait))
        val ai = GuidedSessions.aiContext(started, env, emptyList())
        assertEquals(setups[0], ai.rodSetups[1])
    }
}
