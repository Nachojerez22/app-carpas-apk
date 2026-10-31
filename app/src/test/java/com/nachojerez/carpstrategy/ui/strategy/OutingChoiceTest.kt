package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Elegir día y hora de la salida (fase 7.27). */
class OutingChoiceTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private val point = GeoPoint(38.35, -6.7)
    private val day = LocalDate.of(2026, 10, 31)
    private val legal = LegalWindow.of(SolarCalculator.sunTimes(day, point))!!
    private fun at(h: Int, m: Int = 0): Instant = day.atTime(h, m).atZone(zone).toInstant()

    @Test
    fun `hoy por la manana se ofrecen las horas de hoy que quedan, sin noche`() {
        val days = outingDays(at(10), point, zone)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), days.map { it.date })
        val today = days.first().hours
        assertEquals(at(11), today.first())
        assertTrue(today.contains(at(17)))
        days.forEach { d ->
            val window = LegalWindow.of(SolarCalculator.sunTimes(d.date, point))!!
            d.hours.forEach { h ->
                assertFalse(h.isBefore(window.start.plus(PREP_AFTER_LEGAL_START)))
                assertFalse(h.isAfter(window.end.minus(PREP_AFTER_LEGAL_START)))
            }
        }
    }

    @Test
    fun `cada dia empieza media hora despues del inicio legal y sigue en horas en punto`() {
        val tomorrow = outingDays(at(10), point, zone)[1]
        val start = LegalWindow.of(SolarCalculator.sunTimes(tomorrow.date, point))!!.start.plus(PREP_AFTER_LEGAL_START)
        assertEquals(start, tomorrow.hours.first())
        tomorrow.hours.drop(1).forEach { assertEquals(0, it.atZone(zone).minute) }
    }

    @Test
    fun `por la noche hoy ya no se ofrece`() {
        val days = outingDays(legal.end.plusSeconds(60), point, zone)
        assertEquals(day.plusDays(1), days.first().date)
    }

    @Test
    fun `la elegida manda si sigue valiendo, si no la de siempre`() {
        val now = at(10)
        val days = outingDays(now, point, zone)
        val default = nextOuting(now, point, zone)
        assertEquals(day.plusDays(1), default!!.atZone(zone).toLocalDate())
        assertEquals(at(17), chooseOuting(at(17), days, default))
        assertEquals(default, chooseOuting(null, days, default))
        // Una hora que ya pasó (o a deshora) no vale: vuelve a la de siempre.
        assertEquals(default, chooseOuting(at(9), days, default))
        assertEquals(default, chooseOuting(at(17, 30), days, default))
        assertNull(chooseOuting(null, emptyList(), default))
    }

    @Test
    fun `al cambiar de dia se mantiene la hora si existe`() {
        val days = outingDays(at(10), point, zone)
        val tomorrow = days[1]
        val switched = switchDay(tomorrow, at(17), zone)
        assertEquals(LocalTime.of(17, 0), switched.atZone(zone).toLocalTime())
        assertEquals(tomorrow.date, switched.atZone(zone).toLocalDate())
        // La primera hora de mañana (no en punto) no existe hoy: se toma la primera de hoy.
        assertEquals(days[0].hours.first(), switchDay(days[0], tomorrow.hours.first(), zone))
    }
}
