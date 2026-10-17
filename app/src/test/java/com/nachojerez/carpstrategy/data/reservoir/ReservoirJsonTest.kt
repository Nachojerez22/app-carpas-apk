package com.nachojerez.carpstrategy.data.reservoir

import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.reservoir.FishSpecies
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirRegime
import com.nachojerez.carpstrategy.domain.reservoir.ReservoirSheet
import com.nachojerez.carpstrategy.domain.reservoir.Reservoirs
import com.nachojerez.carpstrategy.domain.reservoir.SheetNote
import com.nachojerez.carpstrategy.domain.reservoir.SpeciesStatus
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.io.File
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReservoirJsonTest {
    private val catalog = ReservoirJson.catalog(File("src/main/assets/embalses.json").readText())

    @Test
    fun `el catalogo real se lee, esta en Extremadura y no repite ids`() {
        assertTrue(catalog.size > 600)
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        assertTrue(catalog.all { it.point.latitude in 37.9..40.6 && it.point.longitude in -7.7..-4.5 && it.name.isNotBlank() })
        val brovales = catalog.single { it.id == Reservoirs.BROVALES_ID }
        assertEquals(ReservoirRegime.GENERAL, brovales.regime)
        val burguillos = catalog.single { it.name == "Embalse de Burguillos" }
        assertEquals(ReservoirRegime.COTO, burguillos.regime)
        assertEquals("Charco El Toro", burguillos.coto)
        assertTrue(burguillos.onlinePermit)
        assertEquals(38.40, burguillos.point.latitude, 0.01)
        assertEquals(-6.60, burguillos.point.longitude, 0.01)
        assertTrue(catalog.any { it.waterSupplyByName })
    }

    @Test
    fun `propios y fichas ida y vuelta`() {
        val own = listOf(Reservoirs.custom("Mi charca", GeoPoint(38.5, -6.5), "1"))
        assertEquals(own, ReservoirJson.decodeCustom(ReservoirJson.encodeCustom(own)))
        val sheet = ReservoirSheet(
            "x", mapOf(FishSpecies.BARBEL to SpeciesStatus(true, false), FishSpecies.CRAYFISH to SpeciesStatus(false, true)),
            groundbaitAllowed = false, aiWaterSupply = true, summary = "Abastecimiento", notes = listOf(SheetNote("Cola somera", Evidence.PURPLE)),
            aiGeneratedAt = Instant.parse("2026-10-17T08:00:00Z"), updatedAt = Instant.parse("2026-10-17T09:00:00Z"),
        )
        assertEquals(listOf(sheet), ReservoirJson.decodeSheets(ReservoirJson.encodeSheets(listOf(sheet))))
        assertEquals(emptyList<ReservoirSheet>(), ReservoirJson.decodeSheets("roto"))
        assertEquals(emptyList<Any>(), ReservoirJson.catalog("{}"))
    }
}
