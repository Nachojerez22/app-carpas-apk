package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiCall
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
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
        // Con 404 se prueban las tres formas de llamar antes de rendirse.
        repeat(3) { server.enqueue(MockResponse(code = 404, body = "{}")) }
        assertEquals(404, assertThrows<AiException.Failed> { client.complete(gemini, "k", "s", "u") }.code)
        server.enqueue(MockResponse(body = """{"candidates":[{"finishReason":"SAFETY"}]}"""))
        assertThrows<AiException.Empty> { client.complete(gemini, "k", "s", "u") }
        assertThrows<AiException.NotConfigured> { client.complete(gemini, " ", "s", "u") }
    }

    @Test
    fun `lista los modelos de texto de tu clave, primero los flash`() {
        server.enqueue(
            MockResponse(
                body = """{"models":[
                    {"name":"models/gemini-pro-x","supportedGenerationMethods":["generateContent","countTokens"]},
                    {"name":"models/text-embedding-x","supportedGenerationMethods":["embedContent"]},
                    {"name":"models/gemini-flash-x","supportedGenerationMethods":["generateContent"]}
                ]}""",
            ),
        )
        assertEquals(listOf("gemini-flash-x", "gemini-pro-x"), client.listModels(gemini, "k"))
        val request = server.takeRequest()
        assertEquals("/v1beta/models", request.url.encodedPath)
        assertEquals("k", request.headers["x-goog-api-key"])
        server.enqueue(MockResponse(body = """{"data":[{"id":"modelo-b"},{"id":"modelo-a"}]}"""))
        val openAi = AiConfig(AiProvider.OPENAI_COMPATIBLE, "x", server.url("/v1").toString())
        assertEquals(listOf("modelo-a", "modelo-b"), client.listModels(openAi, "k"))
        assertEquals("/v1/models", server.takeRequest().url.encodedPath)
        server.enqueue(MockResponse(code = 403, body = "{}"))
        assertThrows<AiException.Unauthorized> { client.listModels(gemini, "k") }
    }

    @Test
    fun `con 404 prueba la clave en la URL y la version v1, y muestra el mensaje sin la clave`() {
        val notFound = """{"error":{"code":404,"message":"models/m is not found for clave-secreta","status":"NOT_FOUND"}}"""
        server.enqueue(MockResponse(code = 404, body = notFound))
        server.enqueue(MockResponse(code = 404, body = notFound))
        server.enqueue(MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        assertEquals("{}", client.complete(gemini, "clave-secreta", "s", "u"))
        val first = server.takeRequest()
        assertEquals("/v1beta/models/gemini-test:generateContent", first.url.encodedPath)
        val second = server.takeRequest()
        assertEquals("clave-secreta", second.url.queryParameter("key"))
        val third = server.takeRequest()
        assertEquals("/v1/models/gemini-test:generateContent", third.url.encodedPath)
        assertEquals("clave-secreta", third.headers["x-goog-api-key"])

        repeat(3) { server.enqueue(MockResponse(code = 404, body = notFound)) }
        val error = assertThrows<AiException.Failed> { client.complete(gemini, "clave-secreta", "s", "u") }
        assertEquals(404, error.code)
        assertEquals("models/m is not found for …", error.detail)
    }

    @Test
    fun `apunta el consumo de cada respuesta y los errores de cuota`() {
        val calls = mutableListOf<AiCall>()
        val counted = AiClient(OkHttpClient(), server.url("/v1beta/").toString(), recorder = { calls += it })
        server.enqueue(
            MockResponse(
                body = """{"candidates":[{"content":{"parts":[{"text":"{}"}]},"groundingMetadata":{"webSearchQueries":["a","b"]}}],""" +
                    """"usageMetadata":{"promptTokenCount":120,"candidatesTokenCount":30,"thoughtsTokenCount":50,"toolUsePromptTokenCount":10}}""",
            ),
        )
        counted.complete(gemini, "k", "s", "u", web = true, kind = AiKind.BAIT)
        val ok = calls.single()
        assertEquals(AiKind.BAIT, ok.kind)
        assertEquals(130, ok.inputTokens)
        assertEquals(30, ok.outputTokens)
        assertEquals(50, ok.thinkingTokens)
        assertEquals(2, ok.searches)
        server.enqueue(MockResponse(code = 429, body = """{"error":{"message":"Quota exceeded for requests per day"}}"""))
        assertThrows<AiException.Quota> { counted.complete(gemini, "k", "s", "u", kind = AiKind.PLAN) }
        val quota = calls.last()
        assertTrue(quota.quota)
        assertEquals("Quota exceeded for requests per day", quota.quotaDetail)
    }

    @Test
    fun `si se corta la conexion lo repite una vez`() {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        server.enqueue(MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        assertEquals("{}", client.complete(gemini, "k", "s", "u"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `dos cortes seguidos dan sin conexion con el motivo y el detalle`() {
        repeat(2) { server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()) }
        val error = assertThrows<AiException.Offline> { client.complete(gemini, "k", "s", "u") }
        assertEquals(OfflineReason.CUT, error.reason)
        assertTrue(error.detail!!.isNotBlank())
    }

    @Test
    fun `motivo de cada fallo de red`() {
        assertEquals(OfflineReason.NO_DNS, AiException.Offline(UnknownHostException("x")).reason)
        assertEquals(OfflineReason.TIMEOUT, AiException.Offline(SocketTimeoutException("x")).reason)
        assertEquals(OfflineReason.TLS, AiException.Offline(SSLHandshakeException("x")).reason)
        assertEquals(OfflineReason.REFUSED, AiException.Offline(ConnectException("x")).reason)
        assertEquals(OfflineReason.CUT, AiException.Offline(IOException("x")).reason)
    }

    @Test
    fun `un fallo al apuntar el consumo no estropea la consulta`() {
        val broken = AiClient(OkHttpClient(), server.url("/v1beta/").toString(), recorder = { error("BD") })
        server.enqueue(MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        assertEquals("{}", broken.complete(gemini, "k", "s", "u"))
    }

    @Test
    fun `un 503 de Google se repite una vez y un 400 no`() {
        server.enqueue(MockResponse(code = 503, body = """{"error":{"message":"overloaded"}}"""))
        server.enqueue(MockResponse(body = """{"candidates":[{"content":{"parts":[{"text":"{}"}]}}]}"""))
        assertEquals("{}", client.complete(gemini, "k", "s", "u"))
        assertEquals(2, server.requestCount)
        server.enqueue(MockResponse(code = 400, body = """{"error":{"message":"bad"}}"""))
        val error = assertThrows<AiException.Failed> { client.complete(gemini, "k", "s", "u") }
        assertEquals("HTTP 400: bad", error.description)
        assertEquals(3, server.requestCount)
    }
}
