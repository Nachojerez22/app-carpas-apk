package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.assistant.AiOutcome
import com.nachojerez.carpstrategy.data.rules.RulesJson
import com.nachojerez.carpstrategy.domain.advice.PlanSource
import com.nachojerez.carpstrategy.domain.assistant.AiAdvice
import com.nachojerez.carpstrategy.domain.assistant.AiExchange
import com.nachojerez.carpstrategy.domain.assistant.AiGroundbait
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiMixPart
import com.nachojerez.carpstrategy.domain.assistant.AiPlan
import com.nachojerez.carpstrategy.domain.assistant.AiRodPlan
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.RecordPeriod
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.usecase.RawWeather
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import java.io.File
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreparedOutingTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")
    private val point = GeoPoint(38.35, -6.70)
    private val rules = RulesJson.parse(File("src/main/assets/rules.json").readText())
    private val result = PrepResult(now, forecastOk = true, plan = PrepPlan.READY, sheetsImproved = 1, sheetsPending = 2)

    private fun state(): StrategyUiState {
        val water = ManualRecord(
            period = RecordPeriod.At(now.minus(Duration.ofHours(1))), location = point, source = "termómetro",
            origin = ManualOrigin.Typed, values = mapOf(ManualField.WATER_TEMP_SURFACE to 18.0), createdAt = now,
        )
        val base = buildStrategyState(RawWeather(null, emptyList(), null, manualRecords = listOf(water)), rules, now, point)
        val spots = listOf(Spot(1, "Punta", structure = SpotStructure.SHALLOW_EDGE), Spot(2, "Caída", structure = SpotStructure.FIRST_DROP))
        val gear = listOf(GearItem("1", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT))
        return base.copy(spots = spots, gear = gear, advice = adviceFor(base.derived, now, emptyList(), gear, spots, emptyList()))
    }

    @Test
    fun `la preparacion guarda condiciones, plan por cana y como fue cada paso`() {
        val state = state()
        val key = planKey(state)!!
        val ai = AiPlan(
            "Plan para el amanecer", Evidence.YELLOW, listOf(AiRodPlan(1, "Caída", "Chufa", reason = "Fondo duro", evidence = Evidence.YELLOW)),
            advice = listOf(AiAdvice(StrategyField.WHERE, "En la caída", Evidence.YELLOW), AiAdvice(StrategyField.BAIT, "Chufa remojada", Evidence.PURPLE)),
            groundbait = AiGroundbait(listOf(AiMixPart("Chufa", 2), AiMixPart("Maíz", 1)), rebait = "Tras cada captura", evidence = Evidence.PURPLE),
        )
        val prepared = AiOutcome(AiExchange(now, AiKind.PLAN, "GEMINI", "modelo", valid = true), ai)
        val dayPlan = dayPlanFor(state, PlanUiState(prepared = prepared, preparedKey = key))!!

        val outing = preparedOutingOf(state, dayPlan, ai.advice, result, now.plusSeconds(60))
        val c = outing.conditions!!
        assertFalse(c.blocked)
        assertEquals(state.result!!.band, c.band)
        assertEquals(18.0, c.waterC)
        assertEquals(state.windows, c.windows)
        assertEquals(PlanSource.AI, outing.planSource)
        assertEquals(dayPlan.rods, outing.rodLines)
        assertEquals(dayPlan.contingencies, outing.contingencies)
        assertTrue(outing.spotAuto)
        // Los consejos de «dónde» ya van en el puesto.
        assertEquals(listOf("Chufa remojada"), outing.aiTips.map { it.text })
        assertEquals(result, outing.result)

        // Ida y vuelta por el JSON del ajuste: no se pierde nada.
        assertEquals(outing, PreparedOutingJson.decode(PreparedOutingJson.encode(outing)))
    }

    @Test
    fun `sin resultado de las reglas no hay plan, pero se guarda como fue la preparacion`() {
        val empty = buildStrategyState(RawWeather(null, emptyList(), null), rules, now, point)
        val outing = preparedOutingOf(empty, null, emptyList(), result.copy(plan = PrepPlan.NOTHING), now)
        assertNull(outing.conditions)
        assertFalse(outing.hasPlan)
        assertEquals(outing, PreparedOutingJson.decode(PreparedOutingJson.encode(outing)))
    }

    @Test
    fun `un JSON roto no se lee y un valor desconocido no tira la preparacion`() {
        assertNull(PreparedOutingJson.decode(null))
        assertNull(PreparedOutingJson.decode("{no es json"))
        val state = state()
        val outing = preparedOutingOf(state, dayPlanFor(state, PlanUiState()), emptyList(), result, now)
        val text = PreparedOutingJson.encode(outing).replace("\"plan_ia\":\"READY\"", "\"plan_ia\":\"OTRO\"")
        val read = PreparedOutingJson.decode(text)
        assertNotNull(read)
        assertEquals(PrepPlan.NOTHING, read!!.result.plan)
        assertEquals(outing.rodLines, read.rodLines)
    }

    @Test
    fun `la cana de cada linea (tipo, longitud) y el peso de la IA van con la preparacion`() {
        val base = state()
        val rods = listOf(
            GearItem("r1", GearCategory.ROD, "Larga", rodKind = RodKind.FIXED, rodLengthM = 9),
            GearItem("r2", GearCategory.ROD, "Carrete", rodKind = RodKind.CARP),
        )
        val state = base.copy(gear = base.gear + rods)
        val key = planKey(state)!!
        val ai = AiPlan("Plan", Evidence.YELLOW, listOf(AiRodPlan(2, baitName = "Chufa", reason = "Plomo de 60 g", evidence = Evidence.YELLOW, weightG = 60.0)))
        val prepared = AiOutcome(AiExchange(now, AiKind.PLAN, "GEMINI", "modelo", valid = true), ai)
        val dayPlan = dayPlanFor(state, PlanUiState(prepared = prepared, preparedKey = key))!!
        assertEquals(listOf(RodSetup("Larga", RodKind.FIXED, 9), RodSetup("Carrete", RodKind.CARP)), dayPlan.rods.map { it.setup })
        assertEquals(60.0, dayPlan.rods[1].weightG)

        val outing = preparedOutingOf(state, dayPlan, emptyList(), result, now)
        val read = PreparedOutingJson.decode(PreparedOutingJson.encode(outing))!!
        assertEquals(dayPlan.rods, read.rodLines)
    }

    // Fase 7.21: activar la salida preparada.

    private fun plannedOuting(): PreparedOuting {
        val state = state()
        return preparedOutingOf(state, dayPlanFor(state, PlanUiState()), emptyList(), result, now.minus(Duration.ofHours(14)))
    }

    @Test
    fun `la preparacion guarda el puesto exacto aunque sea automatico y lo recupera del JSON`() {
        val outing = plannedOuting()
        assertTrue(outing.spotAuto)
        assertNotNull(outing.spotId)
        assertEquals(outing.spotName, state().spots.first { it.id == outing.spotId }.name)
        val read = PreparedOutingJson.decode(PreparedOutingJson.encode(outing))!!
        assertEquals(outing.spotId, read.spotId)
        assertEquals(outing.reservoirId, read.reservoirId)
    }

    @Test
    fun `se activa solo hoy, en ese embalse, con plan y dentro del horario legal`() {
        val zone = Formatting.MADRID
        val outing = plannedOuting().copy(reservoirId = "brovales", reservoirName = "Brovales")
        val legalEnd = now.plus(Duration.ofHours(8))
        assertTrue(outing.activatableAt(now, zone, "brovales", "Brovales", legalEnd))
        // Otro embalse, otro día o ya fuera del horario legal: no.
        assertFalse(outing.activatableAt(now, zone, "otro", "Otro", legalEnd))
        assertFalse(outing.activatableAt(now.plus(Duration.ofDays(1)), zone, "brovales", "Brovales", null))
        assertFalse(outing.activatableAt(legalEnd, zone, "brovales", "Brovales", legalEnd))
        // Sin plan o con filtro duro a esa hora: tampoco.
        assertFalse(outing.copy(rodLines = emptyList()).activatableAt(now, zone, "brovales", "Brovales", legalEnd))
        assertFalse(outing.copy(conditions = outing.conditions!!.copy(blocked = true)).activatableAt(now, zone, "brovales", "Brovales", legalEnd))
        // Preparaciones antiguas (sin id del embalse): por nombre.
        val old = outing.copy(reservoirId = null)
        assertTrue(old.activatableAt(now, zone, "brovales", "Brovales", legalEnd))
        assertFalse(old.activatableAt(now, zone, "otro", "Otro", legalEnd))
    }

    @Test
    fun `al activar se empieza con el puesto, su zona, el cebado y las canas del plan`() {
        val outing = plannedOuting()
        val spots = state().spots.map { if (it.id == outing.spotId) it.copy(zone = com.nachojerez.carpstrategy.domain.journal.FishingZone.entries.first()) else it }
        val start = preparedStartOf(outing, spots)
        assertEquals(outing.spotId, start.spot?.id)
        assertEquals(com.nachojerez.carpstrategy.domain.journal.FishingZone.entries.first(), start.zone)
        assertEquals(outing.groundbait, start.groundbait)
        // Sin cañas en Mi equipo: tantas como líneas del plan, sin nombre.
        assertEquals(List(outing.rodLines.size) { "" }, start.rodNames)
        assertTrue(start.planRods.isEmpty())
        // Si el puesto se borró y se volvió a crear con el mismo nombre, se encuentra por nombre.
        val recreated = spots.map { if (it.id == outing.spotId) it.copy(id = 99) else it }
        assertEquals(99L, preparedStartOf(outing, recreated).spot?.id)
        assertNull(preparedStartOf(outing, emptyList()).spot)
    }

    @Test
    fun `al activar con canas de Mi equipo van sus nombres para que la guiada use su tipo`() {
        val lines = plannedOuting().rodLines
        val outing = plannedOuting().copy(
            rodLines = lines.mapIndexed { i, l -> l.copy(setup = if (i == 0) RodSetup("Carrete", RodKind.CARP) else RodSetup("Larga", RodKind.FIXED, 9)) },
        )
        val start = preparedStartOf(outing, emptyList())
        val expected = listOf("Carrete", "Larga").take(outing.rodLines.size)
        assertEquals(expected, start.planRods)
        assertEquals(expected, start.rodNames)
    }
}
