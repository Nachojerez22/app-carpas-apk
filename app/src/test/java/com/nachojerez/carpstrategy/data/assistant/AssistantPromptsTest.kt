package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.ExperienceSummary
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonSource
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.advice.Stat
import com.nachojerez.carpstrategy.domain.guided.ActivityPlace
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.JumpCount
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.WeatherSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssistantPromptsTest {
    private val madrid = ZoneId.of("Europe/Madrid")
    private val t0 = Instant.parse("2026-10-04T07:00:00Z")
    private val gear = listOf(GearItem("1", GearCategory.BAIT, "Maíz dulce", baitType = BaitType.MAIZE))
    private val spots = listOf(Spot(1, "Punta del cauce", facingDeg = 270, depthM = 4.0))

    @Test
    fun `estado del aviso sin ubicacion y con canas, avisos, tiempo y equipo`() {
        val env = GuidedEnv(t0, gear, madrid)
        var s = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.3512, -6.7034), createdAt = t0), listOf("fija"), null, env, spots.first()).session
        s = GuidedSessions.accept(s, 1, env).session
        s = s.copy(guided = s.guided!!.withWeather(WeatherSnapshot(time = t0, airC = 18.0, windKmh = 12.0, windFromDeg = 270.0)))
        s = GuidedSessions.checkIn(s, 1, CheckIn(t0.plus(Duration.ofMinutes(30)), SignalLevel.DIRECT, seenAt = ActivityPlace.SURFACE, jumps = JumpCount.MANY), GuidedEnv(t0.plus(Duration.ofMinutes(30)), gear, madrid)).session
        val now = t0.plus(Duration.ofMinutes(31))
        val text = AssistantPrompts.checkInState(s, FishingPhase.AUTUMN, t0.plus(Duration.ofHours(11)), now, madrid, gear, spots)
        assertFalse(text.contains("38.35"))
        assertFalse(text.contains("-6.70"))
        val o = Json.parseToJsonElement(text).jsonObject
        assertEquals("09:31", o["hora_local"]!!.jsonPrimitive.content)
        assertEquals("AUTUMN", o["fase"]!!.jsonPrimitive.content)
        assertEquals("FACING", o["tiempo_prevision"]!!.jsonObject["viento_en_el_puesto"]!!.jsonPrimitive.content)
        val rod = o["canas"]!!.jsonArray.single().jsonObject
        assertEquals("Maíz dulce", rod["configuracion"]!!.jsonObject["cebo"]!!.jsonPrimitive.content)
        val checkIn = rod["ultimos_avisos"]!!.jsonArray.single().jsonObject
        assertEquals("SURFACE", checkIn["actividad_vista_en"]!!.jsonPrimitive.content)
        assertEquals("MANY", checkIn["saltos"]!!.jsonPrimitive.content)
        assertEquals("Maíz dulce", o["equipo"]!!.jsonObject["cebos"]!!.jsonArray.single().jsonObject["nombre"]!!.jsonPrimitive.content)
        assertEquals("Punta del cauce", o["mis_puestos"]!!.jsonArray.single().jsonObject["nombre"]!!.jsonPrimitive.content)
    }

    @Test
    fun `estado del plan`() {
        val input = PlanInput(
            now = t0, zone = madrid, rods = 2, band = "INTERMEDIATE", limitingLevel = "TEMPERATURE",
            legalStart = t0.minusSeconds(3600), legalEnd = t0.plus(Duration.ofHours(11)),
            windows = listOf(Triple(t0, t0.plusSeconds(7200), "DAWN")),
            advice = listOf(Triple("WHERE", "Zonas que se calientan antes", Evidence.YELLOW)),
            waterC = 19.5, waterMeasured = true, waterTrend3dC = -0.8, season = "AUTUMN",
            windFromDeg = 270.0, windKmh = 14.0, rain24hMm = 0.0, gear = gear, spots = spots,
        )
        val o = Json.parseToJsonElement(AssistantPrompts.planState(input)).jsonObject
        assertEquals(2, o["numero_de_canas"]!!.jsonPrimitive.content.toInt())
        assertEquals("09:00", o["ventanas_sugeridas"]!!.jsonArray.single().jsonObject["desde"]!!.jsonPrimitive.content)
        assertEquals("YELLOW", o["consejos_de_las_reglas"]!!.jsonArray.single().jsonObject["evidencia"]!!.jsonPrimitive.content)
        assertEquals("FACING", o["mis_puestos"]!!.jsonArray.single().jsonObject["viento"]!!.jsonPrimitive.content)
    }

    @Test
    fun `el plan lleva el tipo de cada cana y las normas lo piden`() {
        val input = PlanInput(
            now = t0, zone = madrid, rods = 2, rodSetups = listOf(RodSetup("Larga", RodKind.FIXED, 9), RodSetup("Carrete", RodKind.CARP)),
            band = null, limitingLevel = null, legalStart = null, legalEnd = null, windows = emptyList(), advice = emptyList(),
            waterC = 19.5, waterMeasured = false, waterTrend3dC = null, season = null,
            windFromDeg = null, windKmh = null, rain24hMm = null, gear = gear, spots = spots,
        )
        val rods = Json.parseToJsonElement(AssistantPrompts.planState(input)).jsonObject["canas_de_hoy"]!!.jsonArray
        val fixed = rods[0].jsonObject
        assertEquals("FIJA", fixed["tipo"]!!.jsonPrimitive.content)
        assertEquals(9, fixed["longitud_m"]!!.jsonPrimitive.content.toInt())
        assertEquals("false", fixed["boilies"]!!.jsonPrimitive.content)
        assertEquals("3-4", fixed["flotador_g"]!!.jsonPrimitive.content)
        val carp = rods[1].jsonObject
        assertEquals("CARPFISHING", carp["tipo"]!!.jsonPrimitive.content)
        assertEquals(listOf("BOTTOM", "POPUP"), carp["columnas_permitidas"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("40-90", carp["plomo_g"]!!.jsonPrimitive.content)
        assertTrue(AssistantPrompts.PLAN_SYSTEM.contains("NUNCA con boilies"))
    }

    @Test
    fun `el puesto elegido va en el plan sin coordenadas`() {
        val spot = spots.single().copy(point = GeoPoint(38.351, -6.701), fetchM = 300.0)
        val input = PlanInput(
            now = t0, zone = madrid, rods = 2, band = null, limitingLevel = null, legalStart = null, legalEnd = null,
            windows = emptyList(), advice = emptyList(), waterC = 19.5, waterMeasured = false, waterTrend3dC = null, season = null,
            windFromDeg = 270.0, windKmh = 14.0, rain24hMm = null, gear = gear, spots = listOf(spot), chosenSpot = spot,
        )
        val state = AssistantPrompts.planState(input)
        val chosen = Json.parseToJsonElement(state).jsonObject["puesto_elegido"]!!.jsonObject
        assertEquals("Punta del cauce", chosen["nombre"]!!.jsonPrimitive.content)
        assertEquals("FACING", chosen["viento"]!!.jsonPrimitive.content)
        assertEquals(300.0, chosen["fetch_m"]!!.jsonPrimitive.content.toDouble())
        assertFalse(state.contains("38.351") || state.contains("\"lat\""))
        assertTrue(AssistantPrompts.PLAN_SYSTEM.contains("puesto_elegido"))
    }

    @Test
    fun `las instrucciones recogen las normas y el objetivo`() {
        listOf(AssistantPrompts.CHECK_IN_SYSTEM, AssistantPrompts.PLAN_SYSTEM, AssistantPrompts.REVIEW_SYSTEM).forEach { s ->
            assertTrue(s.contains("MÁXIMO de carpas"))
            assertTrue(s.contains("Sin porcentajes"))
            // Fase 7.13: en los textos, sin gramos; los kilos solo en el engodo y con la medida del usuario.
            assertTrue(s.contains("sin gramos ni kilos"))
            assertTrue(s.contains("tu_normal_kg_por_cana"))
            assertTrue(s.contains("No propongas horas de noche"))
            assertTrue(s.contains("no hables de normativa"))
            assertTrue(s.contains("pesan 0"))
            assertTrue(s.contains("🟢"))
        }
        assertEquals(AssistantPrompts.PLAN_SYSTEM, AssistantPrompts.withKnowledge(AssistantPrompts.PLAN_SYSTEM, null))
        assertTrue(AssistantPrompts.withKnowledge(AssistantPrompts.PLAN_SYSTEM, "## 5. Estrategias").endsWith("## 5. Estrategias"))
    }

    @Test
    fun `tus datos, tus lecciones y la recomendacion de la app van en el estado`() {
        val exp = ExperienceSummary(
            sessions = 3, rodHours = 30.0, catches = 6,
            baits = listOf(Stat("Chufa", 10.0, 5, 3)), baitTypes = emptyList(), spots = emptyList(), columns = emptyList(),
            dayParts = listOf(Stat(DayPart.DUSK, 8.0, 4, 3), Stat(DayPart.NIGHT, 1.0, 0, 1)),
        )
        val lesson = Lesson("1", t0, LessonTopic.BAIT, "Chufa al anochecer", LessonConditions(phase = FishingPhase.AUTUMN), source = LessonSource.AI, status = LessonStatus.ACCEPTED)
        val input = PlanInput(
            now = t0, zone = madrid, rods = 2, band = null, limitingLevel = null,
            legalStart = Instant.parse("2026-10-04T05:00:00Z"), legalEnd = Instant.parse("2026-10-04T18:30:00Z"),
            windows = emptyList(), advice = emptyList(), waterC = null, waterMeasured = false, waterTrend3dC = null, season = null,
            windFromDeg = null, windKmh = null, rain24hMm = null, gear = gear, spots = spots, extras = AiExtras(exp, listOf(lesson)),
        )
        val o = Json.parseToJsonElement(AssistantPrompts.planState(input)).jsonObject
        assertEquals("07:00–20:30", o["horario_de_pesca"]!!.jsonPrimitive.content)
        val worked = o["lo_que_te_ha_funcionado"]!!.jsonObject
        assertEquals("true", worked["cebos"]!!.jsonArray.single().jsonObject["le_ha_funcionado"]!!.jsonPrimitive.content)
        assertEquals(1, worked["momentos_del_dia"]!!.jsonArray.size)
        assertEquals("Chufa al anochecer", o["lecciones_aceptadas"]!!.jsonArray.single().jsonObject["texto"]!!.jsonPrimitive.content)
        assertFalse(o.containsKey("fin_legal"))
    }

    @Test
    fun `estado del repaso de una sesion`() {
        val env = GuidedEnv(t0, gear, madrid)
        var s = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.3512, -6.7034), createdAt = t0), listOf("fija"), null, env, spots.first()).session
        s = GuidedSessions.accept(s, 1, env).session
        s = GuidedSessions.checkIn(s, 1, CheckIn(t0.plus(Duration.ofMinutes(40)), activity = com.nachojerez.carpstrategy.domain.guided.HookActivity.CATCH), GuidedEnv(t0.plus(Duration.ofMinutes(40)), gear, madrid)).session
        s = GuidedSessions.finish(s, t0.plus(Duration.ofHours(2)))
        val text = AssistantPrompts.reviewState(s, FishingPhase.AUTUMN, madrid, gear, spots, AiExtras())
        assertFalse(text.contains("38.35"))
        val o = Json.parseToJsonElement(text).jsonObject
        val session = o["sesion"]!!.jsonObject
        assertEquals("09:00", session["inicio"]!!.jsonPrimitive.content)
        assertEquals(1, session["carpas"]!!.jsonPrimitive.content.toInt())
        val tramo = session["canas_detalle"]!!.jsonArray.single().jsonObject["tramos"]!!.jsonArray.single().jsonObject
        assertEquals("Maíz dulce", tramo["cebo"]!!.jsonPrimitive.content)
        assertEquals(1, tramo["carpas"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `el plan lleva tus cebos con marca y que lleva, y el cebado con tu medida`() {
        val baits = listOf(GearItem("m", GearCategory.BAIT, "Mix verde", baitType = BaitType.GROUNDBAIT_MIX, brand = "Marca", description = "Harinas y cáñamo"))
        val input = PlanInput(
            now = t0, zone = madrid, rods = 2, band = null, limitingLevel = null, legalStart = null, legalEnd = null,
            windows = emptyList(), advice = emptyList(), waterC = 19.5, waterMeasured = false, waterTrend3dC = null, season = null,
            windFromDeg = null, windKmh = null, rain24hMm = null, gear = baits, spots = spots,
            groundbait = GroundbaitLevel.NORMAL, groundbaitNormalKg = 1.5,
        )
        val o = Json.parseToJsonElement(AssistantPrompts.planState(input)).jsonObject
        val cebado = o["cebado"]!!.jsonObject
        assertEquals("NORMAL", cebado["nivel"]!!.jsonPrimitive.content)
        assertEquals(1.5, cebado["tu_normal_kg_por_cana"]!!.jsonPrimitive.content.toDouble())
        val bait = o["equipo"]!!.jsonObject["cebos"]!!.jsonArray.single().jsonObject
        assertEquals("Marca", bait["marca"]!!.jsonPrimitive.content)
        assertEquals("Harinas y cáñamo", bait["que_lleva"]!!.jsonPrimitive.content)
        // Sin medida: el nivel, pero sin kilos.
        val noMeasure = Json.parseToJsonElement(AssistantPrompts.planState(input.copy(groundbaitNormalKg = null))).jsonObject["cebado"]!!.jsonObject
        assertFalse(noMeasure.containsKey("tu_normal_kg_por_cana"))
        // La ficha de un cebo solo lleva el producto.
        val state = AssistantPrompts.baitState(baits.single().copy(url = "https://t.example/mix"))
        assertTrue(state.contains("https://t.example/mix") && state.contains("Marca") && !state.contains("lat"))
    }
}
