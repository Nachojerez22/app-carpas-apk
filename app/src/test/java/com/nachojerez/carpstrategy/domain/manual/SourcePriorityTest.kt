package com.nachojerez.carpstrategy.domain.manual

import com.nachojerez.carpstrategy.domain.manual.DataSource.AEMET
import com.nachojerez.carpstrategy.domain.manual.DataSource.MANUAL
import com.nachojerez.carpstrategy.domain.manual.DataSource.MODELS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SourcePriorityTest {
    @Test
    fun `por defecto manual, AEMET y modelos`() {
        assertEquals(listOf(MANUAL, AEMET, MODELS), SourcePriority.DEFAULT.order)
    }

    @Test
    fun `subir y bajar no se sale de la lista`() {
        val p = SourcePriority.DEFAULT
        assertEquals(listOf(AEMET, MANUAL, MODELS), p.moveUp(AEMET).order)
        assertEquals(p, p.moveUp(MANUAL))
        assertEquals(listOf(MANUAL, MODELS, AEMET), p.moveDown(AEMET).order)
        assertEquals(p, p.moveDown(MODELS))
    }

    @Test
    fun `desactivar y volver a activar pone la fuente al final`() {
        val withoutAemet = SourcePriority.DEFAULT.toggle(AEMET)
        assertEquals(listOf(MANUAL, MODELS), withoutAemet.order)
        assertFalse(withoutAemet.isEnabled(AEMET))
        assertEquals(listOf(MANUAL, MODELS, AEMET), withoutAemet.toggle(AEMET).order)
    }

    @Test
    fun `nunca queda sin fuentes`() {
        val onlyModels = SourcePriority(listOf(MODELS))
        assertEquals(onlyModels, onlyModels.toggle(MODELS))
        assertThrows<IllegalArgumentException> { SourcePriority(emptyList()) }
        assertThrows<IllegalArgumentException> { SourcePriority(listOf(AEMET, AEMET)) }
    }

    @Test
    fun `se guarda y se recupera, tolerando valores corruptos`() {
        val p = SourcePriority(listOf(AEMET, MODELS))
        assertEquals(p, SourcePriority.decode(p.encode()))
        assertEquals("AEMET,MODELS", p.encode())
        assertEquals(SourcePriority.DEFAULT, SourcePriority.decode(null))
        assertEquals(SourcePriority.DEFAULT, SourcePriority.decode("XYZ"))
        assertEquals(listOf(MODELS, MANUAL), SourcePriority.decode(" MODELS ,FOO,MANUAL,MODELS").order)
    }
}
