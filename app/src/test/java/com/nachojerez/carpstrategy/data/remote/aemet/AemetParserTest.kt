package com.nachojerez.carpstrategy.data.remote.aemet

import com.nachojerez.carpstrategy.testutil.Resources
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AemetParserTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val latin9 = charset("ISO-8859-15")

    @ParameterizedTest
    @CsvSource(
        "383218N, 38.538333",
        "065800W, -6.966667",
        "394924N, 39.823333",
        "0034100W, -3.683333",
        "282000S, -28.333333",
        "001000E, 0.166667",
    )
    fun `coordenadas en grados-minutos-segundos`(dms: String, expected: Double) {
        assertEquals(expected, AemetParser.parseDms(dms)!!, 1e-6)
    }

    @ParameterizedTest
    @CsvSource("''", "N", "12N", "38A218N", "386018N", "383260N", "383218X")
    fun `coordenadas malformadas devuelven null`(dms: String) {
        assertNull(AemetParser.parseDms(dms))
    }

    @Test
    fun `numeros como numero, texto con coma, Ip y vacios`() {
        assertEquals(18.4, AemetParser.parseNumber(JsonPrimitive(18.4)))
        assertEquals(18.4, AemetParser.parseNumber(JsonPrimitive("18,4")))
        assertEquals(-2.5, AemetParser.parseNumber(JsonPrimitive(" -2,5 ")))
        assertEquals(1018.0, AemetParser.parseNumber(JsonPrimitive("1018")))
        assertEquals(0.0, AemetParser.parseNumber(JsonPrimitive("Ip")))
        assertNull(AemetParser.parseNumber(JsonPrimitive("")))
        assertNull(AemetParser.parseNumber(JsonPrimitive("Acum")))
        assertNull(AemetParser.parseNumber(JsonNull))
        assertNull(AemetParser.parseNumber(null))
    }

    @Test
    fun `fint con y sin zona horaria se interpreta en UTC`() {
        val expected = Instant.parse("2026-10-03T06:00:00Z")
        assertEquals(expected, AemetParser.parseInstant("2026-10-03T06:00:00"))
        assertEquals(expected, AemetParser.parseInstant("2026-10-03T06:00:00+0000"))
        assertEquals(expected, AemetParser.parseInstant("2026-10-03T06:00:00Z"))
        assertEquals(expected, AemetParser.parseInstant("2026-10-03T08:00:00+02:00"))
        assertNull(AemetParser.parseInstant("ayer"))
    }

    @Test
    fun `inventario de estaciones, descartando las malformadas`() {
        val stations = AemetParser.parseStations(json, Resources.text("/aemet/stations.json", latin9))
        assertEquals(listOf("TEST1", "TEST2", "TEST3", "TEST4"), stations.map { it.id })
        val jerez = stations.first()
        assertEquals("JEREZ DE LOS CABALLEROS (PRUEBA)", jerez.name)
        assertEquals("BADAJOZ", jerez.province)
        assertEquals(38.316667, jerez.point.latitude, 1e-6)
        assertEquals(-6.766667, jerez.point.longitude, 1e-6)
        assertEquals(511.0, jerez.altitudeM)
    }

    @Test
    fun `observaciones ordenadas, con viento en km-h y decimales con coma`() {
        val observations = AemetParser.parseObservations(json, Resources.text("/aemet/observations.json", latin9))
        assertEquals(
            listOf("2026-10-03T05:00:00Z", "2026-10-03T06:00:00Z", "2026-10-03T07:00:00Z").map(Instant::parse),
            observations.map { it.time },
        )
        val fiveUtc = observations[0]
        assertEquals(16.9, fiveUtc.temperatureC)
        assertEquals(1018.6, fiveUtc.pressureMslHpa)
        assertEquals(3.6, fiveUtc.windSpeedKmh!!, 1e-9)
        assertEquals(12.6, fiveUtc.windGustKmh!!, 1e-9)
        assertEquals(0.0, fiveUtc.precipitationMm, "Ip = inapreciable")
        assertEquals(83.0, fiveUtc.relativeHumidityPct)

        val sixUtc = observations[1]
        assertEquals(16.4, sixUtc.temperatureC)
        assertEquals(5.4, sixUtc.windSpeedKmh!!, 1e-9)
        assertEquals(200.0, sixUtc.windDirectionDeg)

        assertNull(observations[2].pressureMslHpa)
    }

    @Test
    fun `sobre de la primera llamada`() {
        val envelope = AemetParser.parseEnvelope(json, Resources.text("/aemet/envelope_404.json"))
        assertEquals(404, envelope.estado)
        assertNull(envelope.datos)
    }
}
