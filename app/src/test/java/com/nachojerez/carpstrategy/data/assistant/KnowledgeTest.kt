package com.nachojerez.carpstrategy.data.assistant

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KnowledgeTest {
    private val asset = File("src/main/assets/conocimiento.md").readText()

    @Test
    fun `la copia de la app es identica al documento`() {
        assertEquals(File("../docs/CONOCIMIENTO.md").readText(), asset)
    }

    @Test
    fun `extractos para la IA`() {
        val plan = Knowledge.forPlan(asset)
        assertTrue(plan.startsWith("## 5."))
        assertTrue(plan.contains("### 5.8"))
        assertTrue(plan.contains("## 6. Brovales"))
        assertFalse(plan.contains("## 7."))
        val checkIn = Knowledge.forCheckIn(asset)
        assertTrue(checkIn.startsWith("### 5.8"))
        assertTrue(checkIn.contains("#### 5.9.2"))
        assertFalse(checkIn.contains("## 6. Brovales"))
        assertEquals("", Knowledge.between("sin secciones", "## 5.", "## 6."))
    }
}
