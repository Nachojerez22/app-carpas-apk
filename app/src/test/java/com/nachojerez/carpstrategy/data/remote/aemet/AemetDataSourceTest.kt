package com.nachojerez.carpstrategy.data.remote.aemet

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.testutil.Resources
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.create
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AemetDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var api: AemetApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        // El interceptor real solo actúa sobre opendata.aemet.es; aquí se añade la cabecera
        // directamente para comprobar que la llamada la lleva.
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header(AemetApi.API_KEY_HEADER, KEY).build())
            }
            .build()
        api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create<AemetApi>()
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun dataSource(key: String = KEY) = AemetDataSource(api, json, key)

    private fun envelopeOk() = MockResponse(
        body = Resources.text("/aemet/envelope_ok.json")
            .replace("__DATA_URL__", server.url("/opendata/sh/datos123").toString()),
    )

    private fun latin9Body(text: String, contentType: String? = "text/plain;charset=ISO-8859-15") =
        MockResponse.Builder()
            .apply { if (contentType != null) addHeader("Content-Type", contentType) }
            .body(Buffer().write(text.toByteArray(charset("ISO-8859-15"))))
            .build()

    @Test
    fun `observaciones en dos pasos con la API key`() = runTest {
        server.enqueue(envelopeOk())
        server.enqueue(latin9Body(Resources.text("/aemet/observations.json", charset("ISO-8859-15"))))

        val observations = dataSource().fetchObservations("TEST1")

        val first = server.takeRequest()
        assertEquals("/opendata/api/observacion/convencional/datos/estacion/TEST1", first.url.encodedPath)
        assertEquals(KEY, first.headers[AemetApi.API_KEY_HEADER])
        assertEquals("/opendata/sh/datos123", server.takeRequest().url.encodedPath)
        assertEquals(3, observations.size)
    }

    @Test
    fun `decodifica ISO-8859-15 aunque la cabecera no declare codificacion`() = runTest {
        val stations = """[{"latitud":"385500N","provincia":"BADAJOZ","altitud":"190","indicativo":"X1","nombre":"MÉRIDA","longitud":"062000W"}]"""
        server.enqueue(envelopeOk())
        server.enqueue(latin9Body(stations, contentType = null))

        val result = dataSource().fetchStations()

        assertEquals("MÉRIDA", result.single().name)
    }

    @Test
    fun `respeta la codificacion declarada en la cabecera`() = runTest {
        val stations = """[{"latitud":"385500N","provincia":"BADAJOZ","altitud":"190","indicativo":"X1","nombre":"CÁCERES","longitud":"062000W"}]"""
        server.enqueue(envelopeOk())
        server.enqueue(MockResponse(headers = headersOf("Content-Type", "application/json; charset=utf-8"), body = stations))

        assertEquals("CÁCERES", dataSource().fetchStations().single().name)
    }

    @Test
    fun `estado 404 en el sobre significa sin datos`() = runTest {
        server.enqueue(MockResponse(body = Resources.text("/aemet/envelope_404.json")))
        val e = assertThrows<DataSourceException> { dataSource().fetchObservations("TEST1") }
        assertEquals(DataError.NO_DATA, e.error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `HTTP 401 con sobre es API key no valida`() = runTest {
        server.enqueue(MockResponse(code = 401, body = Resources.text("/aemet/envelope_401.json")))
        val e = assertThrows<DataSourceException> { dataSource().fetchStations() }
        assertEquals(DataError.UNAUTHORIZED, e.error)
    }

    @Test
    fun `HTTP 429 sin sobre es limite de peticiones`() = runTest {
        server.enqueue(MockResponse(code = 429, body = "Too Many Requests"))
        val e = assertThrows<DataSourceException> { dataSource().fetchStations() }
        assertEquals(DataError.RATE_LIMITED, e.error)
    }

    @Test
    fun `fallo en el segundo paso`() = runTest {
        server.enqueue(envelopeOk())
        server.enqueue(MockResponse(code = 500))
        val e = assertThrows<DataSourceException> { dataSource().fetchStations() }
        assertEquals(DataError.SERVER, e.error)
    }

    @Test
    fun `sobre sin URL de datos es respuesta invalida`() = runTest {
        server.enqueue(MockResponse(body = """{"descripcion":"exito","estado":200}"""))
        val e = assertThrows<DataSourceException> { dataSource().fetchStations() }
        assertEquals(DataError.INVALID_RESPONSE, e.error)
    }

    @Test
    fun `sin API key no se hace ninguna peticion`() = runTest {
        val e = assertThrows<DataSourceException> { dataSource(key = "").fetchStations() }
        assertEquals(DataError.MISSING_API_KEY, e.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `el interceptor solo envia la clave a AEMET`() {
        val interceptor = AemetApiKeyInterceptor(KEY)
        val captured = mutableListOf<okhttp3.Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .addInterceptor { chain ->
                captured += chain.request()
                okhttp3.Response.Builder()
                    .request(chain.request())
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        client.newCall(okhttp3.Request.Builder().url("https://opendata.aemet.es/opendata/api/x").build()).execute().close()
        client.newCall(okhttp3.Request.Builder().url("https://api.open-meteo.com/v1/forecast").build()).execute().close()

        assertEquals(KEY, captured[0].header(AemetApi.API_KEY_HEADER))
        assertNull(captured[1].header(AemetApi.API_KEY_HEADER))
    }

    private companion object {
        const val KEY = "clave-de-prueba"
    }
}
