package com.nachojerez.carpstrategy.data.remote.openmeteo

import com.nachojerez.carpstrategy.data.remote.DataSourceException
import com.nachojerez.carpstrategy.domain.model.DataError
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.model.WeatherModel
import com.nachojerez.carpstrategy.testutil.Resources
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OpenMeteoParserTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val brovales = GeoPoint(38.35, -6.70)
    private val allModels = WeatherModel.entries

    private fun parse(text: String, models: List<WeatherModel> = allModels) =
        OpenMeteoParser.parse(json.decodeFromString(JsonObject.serializer(), text), brovales, models)

    @Test
    fun `lee las tres series de la respuesta real recortada`() {
        val forecast = parse(Resources.text("/openmeteo/forecast_multimodel_brovales.json"))

        assertEquals(allModels.toSet(), forecast.series.keys)
        assertEquals(GeoPoint(38.375, -6.6875), forecast.gridPoint)
        assertEquals(305.0, forecast.elevationM)
        assertEquals("Europe/Madrid", forecast.timezone)
        forecast.series.values.forEach { assertEquals(6, it.size) }

        val firstIcon = forecast.series.getValue(WeatherModel.ICON_EU).first()
        // 26/09/2026 00:00 en Madrid (CEST, UTC+2) = 25/09 22:00 UTC.
        assertEquals(Instant.parse("2026-09-25T22:00:00Z"), firstIcon.time)
        assertEquals(
            "2026-09-26T00:00",
            firstIcon.time.atZone(ZoneId.of("Europe/Madrid")).toLocalDateTime().toString(),
        )
        assertEquals(24.4, firstIcon.temperatureC)
        assertEquals(1019.2, firstIcon.pressureMslHpa)
        assertEquals(4.9, firstIcon.windSpeedKmh)
        assertEquals(343.0, firstIcon.windDirectionDeg)
        assertEquals(12.2, firstIcon.windGustsKmh)
        assertEquals(23.0, firstIcon.cloudCoverPct)
        assertEquals(0.0, firstIcon.precipitationMm)
        assertEquals(0.0, firstIcon.shortwaveRadiationWm2)

        assertEquals(26.3, forecast.series.getValue(WeatherModel.ARPEGE_EUROPE).first().temperatureC)
        assertEquals(5.0, forecast.series.getValue(WeatherModel.ECMWF_IFS025).last().windSpeedKmh)
    }

    @Test
    fun `los valores null se conservan y las horas totalmente vacias se descartan`() {
        val text = """
            {"latitude":38.375,"longitude":-6.6875,"elevation":305.0,"timezone":"Europe/Madrid",
             "hourly":{"time":[1790373600,1790377200,1790380800],
               "temperature_2m_icon_eu":[20.0,null,null],
               "pressure_msl_icon_eu":[1015.0,1016.0,null]}}
        """.trimIndent()
        val hours = parse(text, listOf(WeatherModel.ICON_EU, WeatherModel.ECMWF_IFS025))
            .series
        assertEquals(setOf(WeatherModel.ICON_EU), hours.keys, "ECMWF no viene en la respuesta")
        val icon = hours.getValue(WeatherModel.ICON_EU)
        assertEquals(2, icon.size)
        assertNull(icon[1].temperatureC)
        assertEquals(1016.0, icon[1].pressureMslHpa)
    }

    @Test
    fun `con un solo modelo acepta claves sin sufijo`() {
        val text = """{"hourly":{"time":[1790373600],"temperature_2m":[18.5]}}"""
        val forecast = parse(text, listOf(WeatherModel.ICON_EU))
        assertEquals(18.5, forecast.series.getValue(WeatherModel.ICON_EU).single().temperatureC)
        assertEquals(brovales, forecast.gridPoint)
    }

    @Test
    fun `error de la API`() {
        val e = assertThrows<DataSourceException> {
            parse("""{"error":true,"reason":"Cannot initialize WeatherVariable from invalid String value x"}""")
        }
        assertEquals(DataError.SERVER, e.error)
    }

    @Test
    fun `series con longitud distinta a time son invalidas`() {
        val e = assertThrows<DataSourceException> {
            parse("""{"hourly":{"time":[1,2],"temperature_2m_icon_eu":[1.0]}}""")
        }
        assertEquals(DataError.INVALID_RESPONSE, e.error)
    }

    @Test
    fun `horas en ISO en vez de unixtime son invalidas`() {
        val e = assertThrows<DataSourceException> {
            parse("""{"hourly":{"time":["2026-09-26T00:00"],"temperature_2m_icon_eu":[1.0]}}""")
        }
        assertEquals(DataError.INVALID_RESPONSE, e.error)
    }
}
