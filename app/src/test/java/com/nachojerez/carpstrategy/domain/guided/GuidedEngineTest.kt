package com.nachojerez.carpstrategy.domain.guided

import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedEngineTest {
    private val t0 = Instant.parse("2026-07-15T05:00:00Z")
    private val legalEnd = Instant.parse("2026-07-15T21:00:00Z")
    private val maize = GearItem("1", GearCategory.BAIT, "Maíz dulce", baitType = BaitType.MAIZE)
    private val boilie = GearItem("2", GearCategory.BAIT, "Boilie fresa 20", baitType = BaitType.BOILIE)
    private val tiger = GearItem("3", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT)
    private val zig = GearItem("4", GearCategory.RIG, "Zig 1,5 m", rigType = RigType.ZIG)
    private val inventory = listOf(maize, boilie, tiger, zig)

    private fun ctx(phase: FishingPhase, minutes: Long, inv: List<GearItem> = inventory, pressure: Boolean = false, end: Instant? = legalEnd) =
        GuidedContext(phase, t0.plus(Duration.ofMinutes(minutes)), end, inv, pressure)

    private fun started(phase: FishingPhase): GuidedLog {
        val log = GuidedLog.start(t0, GuidedEngine.initialProposal(ctx(phase, 0)))
        return log.accept(t0)
    }

    private fun rec(log: GuidedLog) = GuidedRecord(listOf(RodTrack(1, "", log)))

    private fun GuidedLog.check(minutes: Long, signals: SignalLevel = SignalLevel.NONE, activity: HookActivity = HookActivity.NOTHING, bait: BaitState = BaitState.NOT_CHECKED, notWorking: Boolean = false) =
        withCheckIn(CheckIn(t0.plus(Duration.ofMinutes(minutes)), signals, activity, bait, notWorking))

    @Test
    fun `fase por el agua y calor extremo`() {
        assertEquals(FishingPhase.HEAT, GuidedEngine.phase(29.0, "verano"))
        assertEquals(FishingPhase.WINTER, GuidedEngine.phase(8.0, "invierno"))
        assertEquals(FishingPhase.AUTUMN, GuidedEngine.phase(15.0, "otono"))
        assertEquals(FishingPhase.SUMMER, GuidedEngine.phase(24.0, null))
    }

    @Test
    fun `plan A con el cebo de la fase que lleva el usuario y sustituto si lo rechaza`() {
        val a = GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, 0))
        assertEquals(BaitType.BOILIE, a.bait)
        assertEquals("Boilie fresa 20", a.baitName)
        assertEquals(Column.BOTTOM, a.column)
        // Rechazado por no tener el cebo: nuevo plan A sin boilie.
        val log = GuidedLog.start(t0, a).reject(t0, RejectReason.NO_BAIT, "se me acabaron")
        val next = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 1)).proposal!!
        assertEquals(StepKind.INITIAL, next.kind)
        assertEquals(BaitType.MAIZE, next.bait)
    }

    @Test
    fun `sin inventario propone el primero de la fase y sin equivalente avisa`() {
        assertEquals(BaitType.MAIZE, GuidedEngine.initialProposal(ctx(FishingPhase.WINTER, 0, inv = emptyList())).bait)
        val onlyPellet = listOf(GearItem("9", GearCategory.BAIT, "Pellet", baitType = BaitType.PELLET), GearItem("8", GearCategory.BAIT, "Pan", baitType = BaitType.BREAD))
        val choice = BaitCatalog.choose(listOf(BaitType.TIGERNUT), onlyPellet, FishingPhase.WINTER)
        assertEquals(BaitType.BREAD, choice.type)
        assertTrue(choice.fallback)
    }

    @Test
    fun `verano sin senales - a los 60 min columna y luego distancia y zona`() {
        var log = started(FishingPhase.SUMMER).check(30).check(55)
        assertNull(GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 55)).proposal)
        val first = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 60)).proposal!!
        assertEquals(StepKind.COLUMN, first.kind)
        assertEquals(Column.POPUP, first.column)
        log = log.withProposal(first).accept(t0.plus(Duration.ofMinutes(60))).check(90).check(120)
        val second = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 120)).proposal!!
        assertEquals(StepKind.DISTANCE, second.kind)
        log = log.withProposal(second).accept(t0.plus(Duration.ofMinutes(120))).check(150).check(180)
        assertEquals(StepKind.ZONE, GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 180)).proposal!!.kind)
    }

    @Test
    fun `dos no funciona en 30 min fuerzan la propuesta aunque no haya pasado el tiempo`() {
        val log = started(FishingPhase.SUMMER).check(10, notWorking = true).check(25, notWorking = true)
        val p = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 25)).proposal!!
        assertTrue(p.forced)
        assertEquals(StepKind.COLUMN, p.kind)
        // Uno solo, o dos separados más de 30 min, no fuerzan.
        assertNull(GuidedEngine.evaluate(started(FishingPhase.SUMMER).check(10, notWorking = true), ctx(FishingPhase.SUMMER, 20)).proposal)
        assertFalse(GuidedEngine.isForced(started(FishingPhase.SUMMER).check(5, notWorking = true).check(40, notWorking = true), t0.plus(Duration.ofMinutes(41))))
    }

    @Test
    fun `en invierno forzar propone pero recuerda la paciencia`() {
        val log = started(FishingPhase.WINTER).check(10, notWorking = true).check(20, notWorking = true)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.WINTER, 20))
        assertEquals(StepKind.DISTANCE, eval.proposal!!.kind)
        assertTrue(GuidedMessage.FORCED_IN_WINTER in eval.messages)
    }

    @Test
    fun `invierno con actividad reciente - paciencia, no se cambia`() {
        val log = started(FishingPhase.WINTER).check(100, signals = SignalLevel.INDIRECT, activity = HookActivity.TOUCHES).check(160)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.WINTER, 160))
        assertNull(eval.proposal)
        assertTrue(GuidedMessage.WINTER_PATIENCE in eval.messages)
    }

    @Test
    fun `senales sin picadas - presentacion con otro cebo, antes con presion alta`() {
        val log = started(FishingPhase.SUMMER).check(30, signals = SignalLevel.DIRECT)
        assertNull(GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 30)).proposal)
        val withPressure = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 30, pressure = true)).proposal!!
        assertEquals(StepKind.PRESENTATION, withPressure.kind)
        assertEquals(BaitType.MAIZE, withPressure.bait) // distinto del boilie actual
    }

    @Test
    fun `toques en dos avisos seguidos - cambio de montaje`() {
        val log = started(FishingPhase.SUMMER).check(30, activity = HookActivity.TOUCHES).check(45, activity = HookActivity.MISSED)
        assertEquals(StepKind.RIG, GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 45)).proposal!!.kind)
    }

    @Test
    fun `tras una captura no se cambia nada`() {
        val log = started(FishingPhase.SUMMER).check(90, activity = HookActivity.CATCH)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 90))
        assertNull(eval.proposal)
        assertTrue(GuidedMessage.KEEP_AFTER_CATCH in eval.messages)
        assertEquals(1, log.current.catches)
    }

    @Test
    fun `cebo desaparecido dos veces - cebo duro con aviso de preparacion`() {
        val log = started(FishingPhase.SUMMER).check(20, bait = BaitState.GONE).check(40, bait = BaitState.GONE)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 40))
        assertEquals(StepKind.ANTI_CRAB, eval.proposal!!.kind)
        assertEquals(BaitType.TIGERNUT, eval.proposal!!.bait)
        assertTrue(GuidedMessage.PREPARE_BAIT in eval.messages)
    }

    @Test
    fun `cerca del fin legal no se cambia de zona`() {
        var log = started(FishingPhase.SUMMER)
        log = log.withProposal(Proposal(StepKind.COLUMN, Situation.NO_SIGNALS, column = Column.POPUP, evidence = com.nachojerez.carpstrategy.domain.rules.Evidence.PURPLE, createdAt = t0)).accept(t0)
        log = log.withProposal(Proposal(StepKind.DISTANCE, Situation.NO_SIGNALS, evidence = com.nachojerez.carpstrategy.domain.rules.Evidence.PURPLE, createdAt = t0)).accept(t0.plusSeconds(60)).check(200)
        val end = t0.plus(Duration.ofMinutes(230))
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 200, end = end))
        assertTrue(GuidedMessage.NO_ZONE_CHANGES_LEGAL in eval.messages)
        assertNull(eval.proposal)
    }

    @Test
    fun `una propuesta pendiente bloquea otras y aceptar abre un tramo nuevo`() {
        val log = started(FishingPhase.SUMMER).check(65)
        val p = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 65)).proposal!!
        val pending = log.withProposal(p)
        assertNull(GuidedEngine.evaluate(pending, ctx(FishingPhase.SUMMER, 70)).proposal)
        val accepted = pending.accept(t0.plus(Duration.ofMinutes(70)))
        assertEquals(2, accepted.segments.size)
        assertEquals(t0.plus(Duration.ofMinutes(70)), accepted.segments.first().end)
        assertEquals(Column.POPUP, accepted.current.column)
        assertEquals(Decision.ACCEPTED, accepted.proposals.last().decision)
    }

    @Test
    fun `proximo aviso - 30 min, pausa tras captura, mas largo si todo esta tranquilo y antes del fin legal`() {
        val log = started(FishingPhase.SUMMER)
        assertEquals(t0.plus(Duration.ofMinutes(30)), GuidedEngine.nextCheckIn(rec(log), FishingPhase.SUMMER, t0, legalEnd))
        val caught = log.check(40, activity = HookActivity.CATCH)
        assertEquals(t0.plus(Duration.ofMinutes(90)), GuidedEngine.nextCheckIn(rec(caught), FishingPhase.SUMMER, t0.plus(Duration.ofMinutes(40)), legalEnd))
        val quiet = log.check(30).check(60).check(90)
        assertEquals(t0.plus(Duration.ofMinutes(135)), GuidedEngine.nextCheckIn(rec(quiet), FishingPhase.SUMMER, t0.plus(Duration.ofMinutes(90)), legalEnd))
        val nearEnd = legalEnd.minus(Duration.ofMinutes(20))
        assertEquals(legalEnd.minus(Duration.ofMinutes(15)), GuidedEngine.nextCheckIn(rec(log.check(1000)), FishingPhase.SUMMER, nearEnd, legalEnd))
        assertNull(GuidedEngine.nextCheckIn(rec(log), FishingPhase.SUMMER, legalEnd.plusSeconds(1), legalEnd))
        assertNotNull(GuidedEngine.nextCheckIn(rec(log), FishingPhase.WINTER, t0, null))
        // Aviso sin contestar: el siguiente cuenta desde él, no se repite al minuto.
        assertEquals(t0.plus(Duration.ofMinutes(60)), GuidedEngine.nextCheckIn(rec(log).withAlarm(t0.plus(Duration.ofMinutes(30))), FishingPhase.SUMMER, t0.plus(Duration.ofMinutes(31)), legalEnd))
    }

    @Test
    fun `pequenos u otras especies - cebo selectivo y, con mucho cebado, cebar menos`() {
        val log = started(FishingPhase.SUMMER)
            .withCheckIn(CheckIn(t0.plus(Duration.ofMinutes(20)), activity = HookActivity.CATCH, species = Species.BARBEL))
        val one = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 20).copy(groundbait = GroundbaitLevel.HIGH))
        assertNull(one.proposal)
        assertTrue(GuidedMessage.REDUCE_GROUNDBAIT in one.messages)
        // Un barbo no es «captura»: no se dice «no cambies nada».
        assertFalse(GuidedMessage.KEEP_AFTER_CATCH in one.messages)
        val two = log.check(40, bait = BaitState.NIBBLED)
        val eval = GuidedEngine.evaluate(two, ctx(FishingPhase.SUMMER, 40).copy(groundbait = GroundbaitLevel.HIGH))
        assertEquals(StepKind.SELECTIVE, eval.proposal!!.kind)
        assertEquals(BaitType.TIGERNUT, eval.proposal!!.bait)
        assertEquals(0, two.current.catches)
        assertEquals(1, two.current.bycatch)
    }

    @Test
    fun `cebado abundante en invierno y cambio de viento avisan`() {
        val log = started(FishingPhase.WINTER)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.WINTER, 10).copy(groundbait = GroundbaitLevel.HIGH, lastWindChange = t0))
        assertTrue(GuidedMessage.WINTER_GROUNDBAIT in eval.messages)
        assertTrue(GuidedMessage.WIND_CHANGED in eval.messages)
        assertFalse(GuidedMessage.WIND_CHANGED in GuidedEngine.evaluate(log, ctx(FishingPhase.WINTER, 200).copy(lastWindChange = t0)).messages)
    }

    @Test
    fun `segunda cana empieza por otro cebo de la fase`() {
        assertEquals(BaitType.BOILIE, GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, 0)).bait)
        assertEquals(BaitType.MAIZE, GuidedEngine.initialProposal(ctx(FishingPhase.SUMMER, 0), variant = 1).bait)
    }

    @Test
    fun `nada en todas cuenta como un momento para espaciar avisos`() {
        val a = started(FishingPhase.SUMMER).check(30).check(60).check(90)
        val b = started(FishingPhase.SUMMER).check(30).check(60).check(90)
        val record = GuidedRecord(listOf(RodTrack(1, "fija", a), RodTrack(2, "carrete", b)))
        assertEquals(t0.plus(Duration.ofMinutes(135)), GuidedEngine.nextCheckIn(record, FishingPhase.SUMMER, t0.plus(Duration.ofMinutes(90)), legalEnd))
    }

    @Test
    fun `tormenta - seguridad y ninguna propuesta mientras dura`() {
        val log = started(FishingPhase.SUMMER).check(10, notWorking = true).check(20, notWorking = true)
        val eval = GuidedEngine.evaluate(log, ctx(FishingPhase.SUMMER, 20).copy(conditions = setOf(FieldCondition.STORM, FieldCondition.HEAVY_RAIN)))
        assertNull(eval.proposal)
        assertTrue(GuidedMessage.STORM_SAFETY in eval.messages)
        assertTrue(GuidedMessage.HEAVY_RAIN_WATCH in eval.messages)
    }

    @Test
    fun `lluvia y entrada de agua - avisos por fase y peldano de la boca del arroyo antes de la zona`() {
        val light = GuidedEngine.evaluate(started(FishingPhase.SUMMER), ctx(FishingPhase.SUMMER, 5).copy(conditions = setOf(FieldCondition.LIGHT_RAIN)))
        assertTrue(GuidedMessage.LIGHT_RAIN_KEEP in light.messages)
        val inflow = setOf(FieldCondition.MUDDY_INFLOW, FieldCondition.STORM)
        val summer = GuidedEngine.evaluate(started(FishingPhase.SUMMER), ctx(FishingPhase.SUMMER, 5).copy(conditions = inflow))
        assertTrue(GuidedMessage.INFLOW_EDGE in summer.messages)
        assertTrue(GuidedMessage.INFLOW_SUMMER_STORM in summer.messages)
        val winter = GuidedEngine.evaluate(started(FishingPhase.WINTER), ctx(FishingPhase.WINTER, 5).copy(conditions = setOf(FieldCondition.HEAVY_RAIN)))
        assertTrue(GuidedMessage.INFLOW_WINTER_AVOID in winter.messages)
        // Calor extremo sin señales: la zona va primero; con agua turbia entrando, antes la boca del arroyo.
        val heat = started(FishingPhase.HEAT).check(30).check(60).check(90)
        val muddy = ctx(FishingPhase.HEAT, 90).copy(conditions = setOf(FieldCondition.MUDDY_INFLOW))
        assertEquals(StepKind.INFLOW, GuidedEngine.evaluate(heat, muddy).proposal!!.kind)
        assertEquals(StepKind.ZONE, GuidedEngine.evaluate(heat, ctx(FishingPhase.HEAT, 90)).proposal!!.kind)
    }
}
