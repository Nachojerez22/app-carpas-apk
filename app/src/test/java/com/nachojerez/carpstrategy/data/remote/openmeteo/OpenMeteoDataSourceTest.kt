package com.nachojerez.carpstrategy.data.remote.openmeteo

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.testutil.Resources
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OpenMeteoDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenMeteoDataSource

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<OpenMeteoApi>()
        dataSource = OpenMeteoDataSource(api, json)
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `pide los tres modelos, las variables, 7 dias pasados y 3 de prevision`() = runTest {
        server.enqueue(MockResponse(body = Resources.text("/openmeteo/forecast_multimodel_brovales.json")))

        val forecast = dataSource.fetchForecast(GeoPoint(38.35, -6.70))

        val url = server.takeRequest().url
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals("38.35", url.queryParameter("latitude"))
        assertEquals("-6.7", url.queryParameter("longitude"))
        assertEquals("icon_eu,meteofrance_arpege_europe,ecmwf_ifs025", url.queryParameter("models"))
        assertEquals(
            "temperature_2m,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m," +
                "cloud_cover,precipitation,shortwave_radiation",
            url.queryParameter("hourly"),
        )
        assertEquals("7", url.queryParameter("past_days"))
        assertEquals("3", url.queryParameter("forecast_days"))
        assertEquals("Europe/Madrid", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals("kmh", url.queryParameter("wind_speed_unit"))
        assertEquals(WeatherModel.entries.toSet(), forecast.series.keys)
    }

    @Test
    fun `HTTP 400 se clasifica como error del servidor con el motivo`() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":true,"reason":"Latitude must be in range of -90 to 90°."}"""))
        val e = assertThrows<DataSourceException> { dataSource.fetchForecast(GeoPoint(38.35, -6.70)) }
        assertEquals(DataError.SERVER, e.error)
        assertEquals(true, e.message!!.contains("Latitude must be"))
    }

    @Test
    fun `HTTP 429 es limite de peticiones`() = runTest {
        server.enqueue(MockResponse(code = 429, body = """{"error":true,"reason":"Too many requests"}"""))
        val e = assertThrows<DataSourceException> { dataSource.fetchForecast(GeoPoint(38.35, -6.70)) }
        assertEquals(DataError.RATE_LIMITED, e.error)
    }

    @Test
    fun `sin conexion es error de red`() = runTest {
        server.close()
        val e = assertThrows<DataSourceException> { dataSource.fetchForecast(GeoPoint(38.35, -6.70)) }
        assertEquals(DataError.NETWORK, e.error)
    }

    @Test
    fun `JSON corrupto es respuesta invalida`() = runTest {
        server.enqueue(MockResponse(body = "{no es json"))
        val e = assertThrows<DataSourceException> { dataSource.fetchForecast(GeoPoint(38.35, -6.70)) }
        assertEquals(DataError.INVALID_RESPONSE, e.error)
    }
}
