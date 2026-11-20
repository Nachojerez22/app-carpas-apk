package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.WindRelation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Qué pluma montar en la fija (CONOCIMIENTO.md §5.9.14), con las plumas del usuario. */
class FloatChoiceTest {
    private fun input(
        depthM: Double? = 1.5,
        band: DepthBand? = null,
        column: Column = Column.BOTTOM,
        windKmh: Double? = 3.0,
        gustKmh: Double? = null,
        wind: WindRelation? = null,
        waterC: Double? = 16.0,
        smallFish: Boolean = false,
    ) = FloatInput(depthM, band, column, windKmh, gustKmh, wind, waterC, smallFish)

    @Test
    fun `calma y poco fondo, pluma ligera con la de menos y la de mas a mano`() {
        val f = FloatChooser.choose(input())!!
        assertEquals(2.4, f.grams)
        assertEquals(1.8, f.lighter)
        assertEquals(3.0, f.heavier)
        assertEquals(listOf(FloatReason.DEPTH, FloatReason.CALM), f.reasons)
    }

    @Test
    fun `agua fria, carpas poco activas, se baja a la mas ligera`() {
        val f = FloatChooser.choose(input(waterC = 10.0))!!
        assertEquals(1.5, f.grams)
        assertNull(f.lighter)
        assertTrue(FloatReason.COLD in f.reasons)
    }

    @Test
    fun `mas fondo y viento de cara, se sube`() {
        val f = FloatChooser.choose(input(depthM = 3.0, windKmh = 15.0, wind = WindRelation.FACING))!!
        assertEquals(4.0, f.grams)
        assertNull(f.heavier)
        assertTrue(FloatReason.WIND_FACING in f.reasons)
        assertFalse(FloatReason.TOO_WINDY in f.reasons)
    }

    @Test
    fun `con mucho viento de cara y rachas ni la de 4 g basta`() {
        val f = FloatChooser.choose(input(depthM = 3.0, windKmh = 25.0, gustKmh = 40.0, wind = WindRelation.FACING))!!
        assertEquals(4.0, f.grams)
        assertTrue(FloatReason.GUSTS in f.reasons)
        assertTrue(FloatReason.TOO_WINDY in f.reasons)
    }

    @Test
    fun `de espaldas el viento cuenta la mitad`() {
        val f = FloatChooser.choose(input(depthM = 3.0, windKmh = 25.0, gustKmh = 40.0, wind = WindRelation.BEHIND))!!
        assertEquals(3.4, f.grams)
        assertTrue(FloatReason.SHELTERED in f.reasons)
        assertFalse(FloatReason.GUSTS in f.reasons)
    }

    @Test
    fun `a media agua la pluma va ligera aunque haya mucho fondo`() {
        val f = FloatChooser.choose(input(depthM = 4.0, column = Column.ZIG))!!
        assertEquals(1.8, f.grams)
        assertTrue(FloatReason.OFF_BOTTOM in f.reasons)
    }

    @Test
    fun `sin medida usa el tramo aconsejado y con pececillos activos sube`() {
        val f = FloatChooser.choose(input(depthM = null, band = DepthBand.MEDIUM, windKmh = 8.0, wind = WindRelation.SIDE, waterC = 20.0, smallFish = true))!!
        assertEquals(4.0, f.grams)
        assertEquals(listOf(FloatReason.DEPTH_BAND, FloatReason.WIND, FloatReason.SMALL_FISH), f.reasons)
    }

    @Test
    fun `sin plumas no hay recomendacion`() {
        assertNull(FloatChooser.choose(input(), floats = emptyList()))
    }
}
