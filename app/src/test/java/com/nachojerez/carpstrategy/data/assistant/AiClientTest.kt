package com.nachojerez.carpstrategy.data.assistant

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AiClient
    private val gemini = AiConfig(AiProvider.GEMINI, "gemini-test")

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AiClient(OkHttpClient(), server.url("/v1beta/").toString())
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `gemini con la clave en la cabecera, instrucciones aparte y sin partes de razonamiento`() {
        server.enqueue(
            MockResponse(
                body = """{"candidates":[{"content":{"parts":[{"text":"pensando…","thought":true},{"text":"{\"ok\":"},{"text":"true}"}]}}]}""",
            ),
        )
        assertEquals("""{"ok":true}""", client.complete(gemini, " CLAVE ", "sistema", "usuario"))
        val request = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:generateContent", request.url.encodedPath)
        assertEquals("CLAVE", request.headers["x-goog-api-key"])
        assertEquals(null, request.url.queryParameter("key"))
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"systemInstruction\""))
        assertTrue(body.contains("\"responseMimeType\":\"application/json\""))
        assertTrue(body.contains("\"text\":\"usuario\""))
    }

    @Test
    fun `compatible con OpenAI`() {
        server.enqueue(MockResponse(body = """{"choices":[{"message":{"role":"assistant","content":"{\"ok\":true}"}}]}"""))
        val config = AiConfig(AiProvider.OPENAI_COMPATIBLE, "modelo-x", server.url("/v1/").toString())
        assertEquals("""{"ok":true}""", client.complete(config, "k", "s", "u"))
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer k", request.headers["Authorization"])
        assertTrue(request.body!!.utf8().contains("\"json_object\""))
    }

    @Test
    fun `errores clasificados`() {
        server.enqueue(MockResponse(code = 400, body = """{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertThrows<AiException.Unauthorized> { client.complete(gemini, "k", "s", "u") }
        server.enqueue(MockResponse(code = 403, body = "{}"))
        assertThrows<AiException.Unauthorized> { client.complete(gemini, "k", "s", "u") }
        server.enqueue(MockResponse(code = 429, body = "{}"))
        assertThrows<AiException.Quota> { client.complete(gemini, "k", "s", "u") }
        server.enqueue(MockResponse(code = 404, body = "{}"))
        assertEquals(404, assertThrows<AiException.Failed> { client.complete(gemini, "k", "s", "u") }.code)
        server.enqueue(MockResponse(body = """{"candidates":[{"finishReason":"SAFETY"}]}"""))
        assertThrows<AiException.Empty> { client.complete(gemini, "k", "s", "u") }
        assertThrows<AiException.NotConfigured> { client.complete(gemini, " ", "s", "u") }
    }
}
