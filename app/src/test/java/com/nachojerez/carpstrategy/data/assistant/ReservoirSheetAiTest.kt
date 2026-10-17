package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.Reservoir
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirContext
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirRegime
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesStatus
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReservoirSheetAiTest {
    private lateinit var server: MockWebServer
    private lateinit var assistant: Assistant
    private val now = Instant.parse("2026-10-17T09:00:00Z")
    private val config = AiConfig(AiProvider.GEMINI, "m")
    private val burguillos = Reservoir(
        "burguillos", "Embalse de Burguillos", "Burguillos Del Cerro", GeoPoint(38.4044, -6.60302),
        ReservoirRegime.COTO, "Charco El Toro", onlinePermit = true,
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        assistant = Assistant(AiClient(OkHttpClient(), server.url("/").toString()))
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun gemini(text: String) = MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(text)}}]}}]}""")

    @Test
    fun `ficha valida sin tocar lo que confirmo el usuario`() {
        server.enqueue(
            gemini(
                """{"especies":[{"especie":"CARP","presente":true},{"especie":"barbel","presente":true},{"especie":"CRAYFISH","presente":true},{"especie":"TROUT","presente":true},{"especie":"TENCH","presente":null}],
                "abastecimiento":true,"resumen":"Embalse pequeño: cebo selectivo y poca partícula.","notas":[{"texto":"Con barbos, boilie o chufa.","evidencia":"🟡"}]}""",
            ),
        )
        val previous = ReservoirSheet("burguillos", mapOf(FishSpecies.CRAYFISH to SpeciesStatus(present = false, confirmedByUser = true)), groundbaitAllowed = false)
        val outcome = assistant.sheet(config, "k", burguillos, previous, now)
        val sheet = outcome.value!!
        assertTrue(outcome.exchange.valid)
        assertEquals(AiKind.SHEET, outcome.exchange.kind)
        // La especie desconocida (TROUT) y la que no sabe (null) no entran; el cangrejo confirmado no cambia.
        assertEquals(setOf(FishSpecies.CARP, FishSpecies.BARBEL, FishSpecies.CRAYFISH), sheet.species.keys)
        assertEquals(SpeciesStatus(present = false, confirmedByUser = true), sheet.species[FishSpecies.CRAYFISH])
        assertEquals(SpeciesStatus(present = true, confirmedByUser = false), sheet.species[FishSpecies.BARBEL])
        assertEquals(setOf(FishSpecies.CARP, FishSpecies.BARBEL), sheet.presentSpecies)
        assertEquals(false, sheet.groundbaitAllowed)
        assertEquals(true, sheet.aiWaterSupply)
        assertEquals(Evidence.YELLOW, sheet.notes.single().evidence)
        assertEquals(now, sheet.aiGeneratedAt)
        // Lo que se manda: datos públicos del catálogo, nunca el GPS del usuario.
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("Charco El Toro"))
        assertTrue(body.contains("especies_confirmadas_por_el_usuario"))
    }

    @Test
    fun `ficha con promesas o sin evidencia no se guarda`() {
        server.enqueue(gemini("""{"especies":[{"especie":"CARP","presente":true}],"resumen":"Garantizado que pican","notas":[{"texto":"Pesca de noche"}]}"""))
        val outcome = assistant.sheet(config, "k", burguillos, null, now)
        assertNull(outcome.value)
        assertFalse(outcome.exchange.valid)
        assertTrue(AiIssue.PROMISE in outcome.exchange.issues)
        assertTrue(AiIssue.NIGHT in outcome.exchange.issues)
        assertTrue(AiIssue.MISSING_EVIDENCE in outcome.exchange.issues)
    }

    @Test
    fun `el embalse va en el estado que se manda a la IA`() {
        val sheet = ReservoirSheet(
            "burguillos",
            mapOf(FishSpecies.BARBEL to SpeciesStatus(true, true), FishSpecies.CATFISH to SpeciesStatus(false, true)),
            summary = "Pequeño",
        )
        val extras = AiExtras(reservoir = ReservoirContext(burguillos, sheet))
        val input = PlanInput(
            now, ZoneId.of("Europe/Madrid"), 2, null, null, null, null, emptyList(), emptyList(), 15.0, false, null, null, null, null, null,
            emptyList(), emptyList(), extras,
        )
        val state = Json.parseToJsonElement(AssistantPrompts.planState(input)).jsonObject
        val embalse = state["embalse"]!!.jsonObject
        assertEquals("Embalse de Burguillos", embalse["nombre"]!!.jsonPrimitive.content)
        assertEquals(listOf("BARBEL"), embalse["especies"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("CATFISH"), embalse["especies_que_no_hay"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("sin_confirmar", embalse["se_puede_cebar"]!!.jsonPrimitive.content)
        assertTrue(AssistantPrompts.PLAN_SYSTEM.contains("§5.11"))
    }

    @Test
    fun `el apartado de Brovales solo va en Brovales`() {
        val md = File("src/main/assets/conocimiento.md").readText()
        assertTrue(Knowledge.forPlan(md, brovales = true).contains("## 6. Brovales"))
        val other = Knowledge.forPlan(md, brovales = false)
        assertTrue(other.startsWith("## 5."))
        assertFalse(other.contains("## 6. Brovales"))
    }
}
