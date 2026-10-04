package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FieldWeatherTest {
    private val t0 = Instant.parse("2026-10-04T08:00:00Z")
    private fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

    private val calm = WeatherSnapshot(
        time = t0, airC = 16.0, windKmh = 10.0, windFromDeg = 270.0, gustKmh = 20.0, cloudPct = 10.0,
        precipitationMm = 0.0, weatherCode = 1, pressureHpa = 1018.0, waterC = 18.0,
        sunset = Instant.parse("2026-10-04T18:10:00Z"), legalEnd = Instant.parse("2026-10-04T19:10:00Z"),
    )

    private fun kinds(from: WeatherSnapshot, to: WeatherSnapshot) = FieldWeather.changes(from, to).map { it.kind }.toSet()

    @Test
    fun `sin cambios por debajo de los umbrales`() {
        val to = calm.copy(time = at(30), airC = 17.0, windKmh = 14.0, windFromDeg = 300.0, cloudPct = 40.0, pressureHpa = 1017.0, waterC = 18.2)
        assertTrue(FieldWeather.changes(calm, to).isEmpty())
    }

    @Test
    fun `aire, viento, rachas, nubes, presion y agua`() {
        val to = calm.copy(
            time = at(30), airC = 19.0, windKmh = 25.0, windFromDeg = 180.0, gustKmh = 45.0,
            cloudPct = 90.0, pressureHpa = 1014.0, waterC = 18.5,
        )
        assertEquals(
            setOf(
                WeatherChangeKind.AIR_UP, WeatherChangeKind.WIND_UP, WeatherChangeKind.WIND_SHIFT, WeatherChangeKind.GUSTS,
                WeatherChangeKind.CLOUDING, WeatherChangeKind.PRESSURE_DOWN, WeatherChangeKind.WATER_UP,
            ),
            kinds(calm, to),
        )
        assertEquals(setOf(WeatherChangeKind.AIR_DOWN, WeatherChangeKind.WIND_DOWN, WeatherChangeKind.CLEARING, WeatherChangeKind.PRESSURE_UP, WeatherChangeKind.WATER_DOWN), kinds(to, calm.copy(gustKmh = 50.0, windFromDeg = 180.0)))
    }

    @Test
    fun `nubes y presion van con peso 0 y etiqueta roja`() {
        listOf(WeatherChangeKind.CLOUDING, WeatherChangeKind.CLEARING, WeatherChangeKind.PRESSURE_UP, WeatherChangeKind.PRESSURE_DOWN).forEach {
            assertTrue(it.weightZero)
            assertEquals(com.nachojerez.carpstrategy.domain.rules.Evidence.RED, it.evidence)
        }
        assertFalse(WeatherChangeKind.STORM.weightZero)
    }

    @Test
    fun `el giro del viento no cuenta con viento flojo`() {
        val weak = calm.copy(windKmh = 3.0)
        assertFalse(WeatherChangeKind.WIND_SHIFT in kinds(weak, weak.copy(windFromDeg = 90.0)))
    }

    @Test
    fun `lluvia y tormenta por precipitacion o codigo`() {
        val rain = calm.copy(time = at(30), precipitationMm = 0.8)
        assertTrue(rain.forecastRain)
        assertEquals(setOf(WeatherChangeKind.RAIN_START), kinds(calm, rain))
        assertEquals(setOf(WeatherChangeKind.RAIN_STOP), kinds(rain, calm))
        val storm = calm.copy(time = at(30), weatherCode = 95)
        assertTrue(storm.forecastStorm)
        assertTrue(storm.forecastRain)
        assertEquals(setOf(WeatherChangeKind.STORM, WeatherChangeKind.RAIN_START), kinds(calm, storm))
        assertTrue(calm.copy(weatherCode = 61).forecastRain)
        assertFalse(calm.copy(weatherCode = 3).forecastRain)
    }

    private val rod = RodTrack(1, "", GuidedLog(listOf(Segment(start = t0, kind = StepKind.INITIAL))))

    @Test
    fun `informe con cambios desde el inicio y desde el ultimo aviso, luz y viento en el puesto`() {
        val spot = Spot(1, "Punta", facingDeg = 270)
        val record = GuidedRecord(rods = listOf(rod), spot = spot)
            .withWeather(calm)
            .withWeather(calm.copy(time = at(30), airC = 17.0))
            .withWeather(calm.copy(time = at(60), airC = 18.0))
        val report = FieldWeather.report(record, at(60))!!
        assertEquals(listOf(WeatherChangeKind.AIR_UP), report.sinceStart.map { it.kind })
        assertTrue(report.sinceLast.isEmpty())
        assertEquals(WindRelation.FACING, report.wind)
        assertEquals(Duration.ofMinutes(550), report.toSunset)
        assertEquals(Duration.ofMinutes(610), report.toLegalEnd)
        assertNull(report.question)
    }

    @Test
    fun `sin tiempo no hay informe y con uno solo no hay cambios`() {
        assertNull(FieldWeather.report(GuidedRecord(rods = listOf(rod)), t0))
        val report = FieldWeather.report(GuidedRecord(rods = listOf(rod)).withWeather(calm), t0)!!
        assertTrue(report.sinceStart.isEmpty())
        assertTrue(report.sinceLast.isEmpty())
        assertNull(report.wind)
    }

    @Test
    fun `pregunta si llueve cuando la prevision y lo anotado no cuadran`() {
        val rainy = GuidedRecord(rods = listOf(rod)).withWeather(calm.copy(precipitationMm = 1.0))
        assertEquals(WeatherQuestion.RAIN, FieldWeather.report(rainy, t0)!!.question)
        // Ya contestado: no se repite en ese aviso.
        assertNull(FieldWeather.report(rainy.withWeatherAnswer(WeatherQuestion.RAIN, false), t0)!!.question)
        // Anotada por el usuario: cuadra.
        assertNull(FieldWeather.report(rainy.withCondition(t0, FieldCondition.LIGHT_RAIN, true), t0)!!.question)
        // Anotada pero la previsión dice seco: se pregunta si sigue lloviendo.
        val marked = GuidedRecord(rods = listOf(rod)).withCondition(t0, FieldCondition.HEAVY_RAIN, true).withWeather(calm)
        assertEquals(WeatherQuestion.RAIN, FieldWeather.report(marked, t0)!!.question)
    }

    @Test
    fun `la tormenta prevista se pregunta antes que la lluvia`() {
        val storm = GuidedRecord(rods = listOf(rod)).withWeather(calm.copy(weatherCode = 96))
        assertEquals(WeatherQuestion.STORM, FieldWeather.report(storm, t0)!!.question)
        val answered = storm.withWeatherAnswer(WeatherQuestion.STORM, false)
        assertEquals(WeatherQuestion.RAIN, FieldWeather.report(answered, t0)!!.question)
    }

    @Test
    fun `un giro claro del viento cuenta como cambio de viento`() {
        val record = GuidedRecord(rods = listOf(rod)).withWeather(calm).withWeather(calm.copy(time = at(30), windFromDeg = 120.0))
        assertEquals(listOf(at(30)), record.windChanges)
        val steady = GuidedRecord(rods = listOf(rod)).withWeather(calm).withWeather(calm.copy(time = at(30)))
        assertTrue(steady.windChanges.isEmpty())
    }

    @Test
    fun `contestar si llueve activa o termina la lluvia y lo que dices manda`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val session = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.2, -6.75), createdAt = t0), listOf(""), null, GuidedEnv(t0, emptyList(), madrid)).session
        val withRain = session.copy(guided = session.guided!!.withWeather(calm.copy(precipitationMm = 1.0)))
        val env = GuidedEnv(at(5), emptyList(), madrid)
        val yes = GuidedSessions.answerWeather(withRain, WeatherQuestion.RAIN, true, env, heavy = true).session
        assertEquals(setOf(FieldCondition.HEAVY_RAIN), yes.guided!!.activeConditions(at(5)))
        assertEquals(true, yes.guided!!.weather.last().rainAnswer)
        val no = GuidedSessions.answerWeather(yes, WeatherQuestion.RAIN, false, GuidedEnv(at(10), emptyList(), madrid)).session
        assertTrue(no.guided!!.activeConditions(at(10)).isEmpty())
        val storm = GuidedSessions.answerWeather(withRain, WeatherQuestion.STORM, true, env).session
        assertTrue(FieldCondition.STORM in storm.guided!!.activeConditions(at(5)))
    }

    @Test
    fun `el puesto se copia al empezar y pone su zona`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val spot = Spot(2, "Recula", zone = com.nachojerez.carpstrategy.domain.journal.FishingZone.NORTH, structure = SpotStructure.INLET_BAY)
        val session = GuidedSessions.start(Session(start = t0, location = GeoPoint(38.2, -6.75), createdAt = t0), listOf(""), null, GuidedEnv(t0, emptyList(), madrid), spot).session
        assertEquals(spot, session.guided!!.spot)
        assertEquals(com.nachojerez.carpstrategy.domain.journal.FishingZone.NORTH, session.zone)
    }
}
