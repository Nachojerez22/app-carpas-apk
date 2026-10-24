package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiContext
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import java.time.Instant
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

class AssistantTest {
    private lateinit var server: MockWebServer
    private lateinit var assistant: Assistant
    private val now = Instant.parse("2026-10-04T09:00:00Z")
    private val ctx = AiContext(now, now.plusSeconds(30_000), setOf(1), emptyList(), emptyList())
    private val config = AiConfig(AiProvider.GEMINI, "m")

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        assistant = Assistant(AiClient(OkHttpClient(), server.url("/").toString()))
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun gemini(text: String) = MockResponse(
        body = """{"candidates":[{"content":{"parts":[{"text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}}]}""",
    )

    @Test
    fun `respuesta valida`() {
        server.enqueue(gemini("""```json
            {"decision":"CAMBIAR","cambios":[{"cana":1,"tipo":"column","accion":"Pon un zig","columna":"ZIG","motivo":"Saltos en superficie","evidencia":"🟡"}],"motivos":["Actividad arriba"],"evidencia":"amarillo"}
            ```"""))
        val outcome = assistant.checkIn(config, "k", "{}", ctx)
        val decision = outcome.value!!
        assertEquals(false, decision.keep)
        assertEquals(StepKind.COLUMN, decision.changes.single().kind)
        assertEquals(Column.ZIG, decision.changes.single().column)
        assertEquals(Evidence.YELLOW, decision.evidence)
        assertTrue(outcome.exchange.valid)
        assertEquals(AiKind.CHECK_IN, outcome.exchange.kind)
        assertEquals("CAMBIAR 1:COLUMN", outcome.exchange.summary)
        assertEquals("Actividad arriba", outcome.exchange.note)
        assertEquals("GEMINI", outcome.exchange.provider)
    }

    @Test
    fun `respuesta que no cumple las normas se registra y no se usa`() {
        server.enqueue(gemini("""{"decision":"MANTENER","cambios":[],"motivos":["Un 80 % de opciones si cebas 300 g"]}"""))
        val outcome = assistant.checkIn(config, "k", "{}", ctx)
        assertNull(outcome.value)
        assertFalse(outcome.exchange.valid)
        assertEquals(listOf(AiIssue.PROBABILITY, AiIssue.GRAMS, AiIssue.MISSING_EVIDENCE), outcome.exchange.issues)
        assertNull(outcome.exchange.note)
    }

    @Test
    fun `texto ilegible y errores de red`() {
        server.enqueue(gemini("no sé"))
        assertEquals(listOf(AiIssue.UNREADABLE), assistant.checkIn(config, "k", "{}", ctx).exchange.issues)
        server.enqueue(MockResponse(code = 429, body = "{}"))
        val quota = assistant.checkIn(config, "k", "{}", ctx)
        assertNull(quota.value)
        assertEquals("Quota", quota.exchange.error)
        server.enqueue(gemini("""{"ok":true}"""))
        assertNull(assistant.test(config, "k"))
        server.enqueue(MockResponse(code = 403, body = "{}"))
        assertTrue(assistant.test(config, "k") is AiException.Unauthorized)
    }

    @Test
    fun `plan`() {
        server.enqueue(
            gemini(
                """{"resumen":"Empieza a fondo","evidencia":"🟡","canas":[{"cana":1,"cebo":null,"columna":"BOTTOM","distancia_m":35,"motivo":"Agua templada","evidencia":"🟢"}],"consejos":[{"apartado":"dónde","texto":"Orilla con sol","evidencia":"🟡"},{"apartado":"AVOID","texto":"Sobrecebar","evidencia":"🟢"}],"avisos":[{"texto":"Recoge al fin legal","evidencia":"⚖"}]}""",
            ),
        )
        val plan = assistant.plan(config, "k", "{}", ctx).value!!
        assertEquals(35.0, plan.rods.single().distanceM)
        assertNull(plan.rods.single().baitName)
        assertEquals(Evidence.REGULATION, plan.warnings.single().evidence)
        assertEquals(listOf(StrategyField.WHERE, StrategyField.AVOID), plan.advice.map { it.field })
    }

    @Test
    fun `plan con el puesto que recomienda la IA`() {
        val text = """{"resumen":"A fondo","evidencia":"🟡","puesto_recomendado":{"puesto":"Punta","motivo":"Recibe el viento cálido","evidencia":"🟡"},"canas":[]}"""
        val best = AssistantJson.parsePlan(text)!!.bestSpot!!
        assertEquals("Punta", best.spotName)
        assertEquals("Recibe el viento cálido", best.reason)
        assertEquals(Evidence.YELLOW, best.evidence)
        assertNull(AssistantJson.parsePlan("""{"resumen":"A fondo","puesto_recomendado":null,"canas":[]}""")!!.bestSpot)
    }

    @Test
    fun `repaso de sesion con lecciones validas y descartadas`() {
        server.enqueue(
            gemini(
                """{"lecciones":[
                {"tema":"BAIT","texto":"La chufa dio carpas al anochecer con agua a 17 °C","condiciones":{"fase":"AUTUMN","momento":"DUSK","agua_min_c":15,"agua_max_c":19},"evidencia":"🟣","apartado":"§5.8","corrige":false},
                {"tema":"GROUNDBAIT","texto":"Ceba 500 g","evidencia":"🟣"},
                {"tema":"WHERE","texto":"La Presa rinde","condiciones":{"puesto":"Presa"},"evidencia":"🟣"}
                ]}""",
            ),
        )
        var n = 0
        val outcome = assistant.review(config, "k", "{}", listOf(com.nachojerez.carpstrategy.domain.guided.Spot(1, "Punta")), now, 7, null) { "L${++n}" }
        val lessons = outcome.value!!
        assertEquals(1, lessons.size)
        val l = lessons.single()
        assertEquals("L1", l.id)
        assertEquals(com.nachojerez.carpstrategy.domain.advice.LessonTopic.BAIT, l.topic)
        assertEquals(com.nachojerez.carpstrategy.domain.advice.LessonStatus.PROPOSED, l.status)
        assertEquals(listOf(7L), l.sessionIds)
        assertEquals(com.nachojerez.carpstrategy.domain.advice.DayPart.DUSK, l.conditions.dayPart)
        assertEquals("§5.8", l.knowledgeRef)
        assertEquals(AiKind.REVIEW, outcome.exchange.kind)
        assertEquals(listOf(AiIssue.UNKNOWN_SPOT, AiIssue.GRAMS), outcome.exchange.issues)
    }

    @Test
    fun `chat de la sesion`() {
        server.enqueue(gemini("""{"respuesta":"Mantén el boilie y acerca la carrete al cortado.","cambios":[{"cana":1,"tipo":"DISTANCE","accion":"Acércala","motivo":"Saltos cerca","evidencia":"🟡"}],"evidencia":"🟡"}"""))
        val outcome = assistant.chat(config, "k", "{}", ctx)
        assertTrue(outcome.exchange.valid)
        assertEquals(AiKind.CHAT, outcome.exchange.kind)
        assertEquals(StepKind.DISTANCE, outcome.value!!.changes.single().kind)
        assertEquals("RESPUESTA 1:DISTANCE", outcome.exchange.summary)
        val body = server.takeRequest().body!!.utf8()
        assertTrue(body.contains("experto en la pesca de la carpa"))
    }
}
