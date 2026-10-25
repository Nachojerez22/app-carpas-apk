package com.nachojerez.carpstrategy.domain.guided

import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PendingSheetsTest {
    private val t = Instant.parse("2026-10-25T18:00:00Z")
    private fun sheet(searched: Boolean, error: String?) =
        BaitSheet(type = null, composition = "x", evidence = Evidence.PURPLE, updatedAt = t, searched = searched, searchError = error)

    @Test
    fun `pendientes son las hechas sin busqueda porque Google no dejo, y sin enlace solo si quedan busquedas`() {
        val withUrl = GearItem("1", GearCategory.BAIT, "Con enlace", url = "https://t.example/a", sheet = sheet(false, "Quota"))
        val noUrl = GearItem("2", GearCategory.BAIT, "Sin enlace", sheet = sheet(false, "Quota"))
        val searched = GearItem("3", GearCategory.BAIT, "Buscada", sheet = sheet(true, null))
        // Proveedor sin búsqueda (no fue Google quien dijo que no): no hay nada que rehacer.
        val otherProvider = GearItem("4", GearCategory.BAIT, "Otro proveedor", sheet = sheet(false, null))
        val noSheet = GearItem("5", GearCategory.BAIT, "Sin ficha")
        val gear = listOf(withUrl, noUrl, searched, otherProvider, noSheet)
        assertEquals(listOf("1", "2"), gear.pendingSheets(searchAllowed = true).map { it.id })
        assertEquals(listOf("1"), gear.pendingSheets(searchAllowed = false).map { it.id })
    }
}
