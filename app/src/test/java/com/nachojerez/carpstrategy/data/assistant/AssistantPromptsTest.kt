package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.guided.ActivityPlace
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedEnv
import com.nachojerez.carpstrategy.domain.guided.GuidedSessions
import com.nachojerez.carpstrategy.domain.guided.JumpCount
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
    fun `las instrucciones recogen las normas`() {
        listOf(AssistantPrompts.CHECK_IN_SYSTEM, AssistantPrompts.PLAN_SYSTEM).forEach { s ->
            assertTrue(s.contains("Sin probabilidades"))
            assertTrue(s.contains("Sin gramos"))
            assertTrue(s.contains("Nunca propongas pescar de noche"))
            assertTrue(s.contains("peso 0") || s.contains("pesan 0"))
            assertTrue(s.contains("🟢"))
        }
    }
}
