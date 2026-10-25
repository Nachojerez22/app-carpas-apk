package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GroundbaitTest {
    @Test
    fun `kilos a partir de tu medida`() {
        val normal = GroundbaitAmounts.amount(GroundbaitLevel.NORMAL, 1.0, 2)!!
        assertEquals(1.0, normal.perRodKg)
        assertEquals(2.0, normal.totalKg)
        assertEquals(0.5, normal.initialPerRodKg)
        assertEquals(0.5, normal.rebaitPerRodKg)
        assertEquals(0.25, GroundbaitAmounts.amount(GroundbaitLevel.LOW, 1.0, 1)!!.perRodKg)
        assertEquals(2.0, GroundbaitAmounts.amount(GroundbaitLevel.HIGH, 1.0, 1)!!.perRodKg)
        // Invierno (poco) frente a verano (mucho): 1/8, dentro de 1/5–1/10 (§5.5).
        assertEquals(1.0 / 8, GroundbaitAmounts.factor(GroundbaitLevel.LOW) / GroundbaitAmounts.factor(GroundbaitLevel.HIGH))
    }

    @Test
    fun `sin medida no hay kilos y la medida se lee con coma`() {
        assertNull(GroundbaitAmounts.amount(GroundbaitLevel.NORMAL, null, 2))
        assertNull(GroundbaitAmounts.amount(GroundbaitLevel.NORMAL, 50.0, 2))
        assertEquals(1.5, GroundbaitAmounts.parse("1,5"))
        assertEquals(0.75, GroundbaitAmounts.parse(" 0.75 "))
        assertNull(GroundbaitAmounts.parse("mucho"))
        assertNull(GroundbaitAmounts.parse("0"))
        assertEquals(3.0, GroundbaitAmounts.maxPerRodKg(1.0))
    }
}
