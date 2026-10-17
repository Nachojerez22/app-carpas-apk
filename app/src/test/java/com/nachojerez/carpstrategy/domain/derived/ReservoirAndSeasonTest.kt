package com.nachojerez.carpstrategy.domain.derived

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReservoirAndSeasonTest {
    private val now = Instant.parse("2026-10-03T10:00:00Z")

    private fun reading(date: String, hm3: Double? = null, pct: Double? = null) =
        LevelReading(Instant.parse("${date}T10:00:00Z"), hm3, pct, "Boletín")

    @Test
    fun `nivel actual y variacion semanal con el patron real de septiembre de 2026`() {
        // CONOCIMIENTO.md §6.1: 5 hm³ el 7/9 (aprox.), 4 hm³ el 14/9 tras perder 1 hm³ en una semana.
        val level = ReservoirModel.evaluate(listOf(reading("2026-09-07", hm3 = 5.0), reading("2026-09-14", hm3 = 4.0)), now)!!
        assertEquals(4.0, level.volumeHm3!!, 1e-9)
        assertEquals(-1.0, level.delta7dHm3!!, 1e-9)
        assertEquals(4.0 / 6.98 * 100, level.percent!!, 1e-9)
    }

    @Test
    fun `convierte porcentaje en volumen con la capacidad oficial`() {
        val level = ReservoirModel.evaluate(listOf(reading("2026-09-28", pct = 57.1)), now)!!
        assertEquals(0.571 * 6.98, level.volumeHm3!!, 1e-9)
        assertEquals(57.1, level.percent!!, 1e-9)
        assertNull(level.delta7dHm3, "Sin lectura de hace ~7 días")
    }

    @Test
    fun `sin lecturas o solo futuras no hay nivel`() {
        assertNull(ReservoirModel.evaluate(emptyList(), now))
        assertNull(ReservoirModel.evaluate(listOf(reading("2026-10-10", hm3 = 4.0)), now))
    }

    @Test
    fun `referencia demasiado lejana no se compara`() {
        val level = ReservoirModel.evaluate(listOf(reading("2026-09-01", hm3 = 6.0), reading("2026-09-28", hm3 = 4.0)), now)!!
        assertNull(level.delta7dHm3)
    }

    @Test
    fun `invierno y verano por la temperatura del agua`() {
        val date = LocalDate.parse("2026-10-03")
        assertEquals(SeasonByWater.WINTER, SeasonModel.classify(8.0, date).season)
        assertEquals(SeasonByWater.SUMMER, SeasonModel.classify(24.0, date).season)
        assertFalse(SeasonModel.classify(24.0, date).fromCalendar)
    }

    @Test
    fun `entre 10 y 22 grados manda la epoca del año`() {
        // Octubre con el agua templada (aunque esté subiendo unos días): otoño, nunca primavera.
        val autumn = SeasonModel.classify(19.2, LocalDate.parse("2026-10-17"))
        assertEquals(SeasonByWater.AUTUMN, autumn.season)
        assertTrue(autumn.fromCalendar)
        assertEquals(SeasonByWater.SPRING, SeasonModel.classify(18.0, LocalDate.parse("2026-05-03")).season)
        assertEquals(SeasonByWater.AUTUMN, SeasonModel.classify(15.0, LocalDate.parse("2026-07-01")).season)
    }
}
