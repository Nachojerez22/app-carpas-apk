package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiContext
import com.nachojerez.carpstrategy.domain.assistant.AiIssue
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
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
                """{"resumen":"Empieza a fondo","evidencia":"🟡","canas":[{"cana":1,"cebo":null,"columna":"BOTTOM","distancia_m":35,"motivo":"Agua templada","evidencia":"🟢"}],"avisos":[{"texto":"Recoge al fin legal","evidencia":"⚖"}]}""",
            ),
        )
        val plan = assistant.plan(config, "k", "{}", ctx).value!!
        assertEquals(35.0, plan.rods.single().distanceM)
        assertNull(plan.rods.single().baitName)
        assertEquals(Evidence.REGULATION, plan.warnings.single().evidence)
    }
}
