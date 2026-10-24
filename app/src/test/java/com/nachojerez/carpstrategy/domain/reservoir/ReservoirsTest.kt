package com.nachojerez.carpstrategy.domain.reservoir

import com.nachojerez.carpstrategy.domain.advice.AdvisorInput
import com.nachojerez.carpstrategy.domain.advice.FishingAdvisor
import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReservoirsTest {
    private val brovales = Reservoir(Reservoirs.BROVALES_ID, "Embalse de Brovales", "Jerez de los Caballeros", GeoPoint(38.35, -6.70))
    private val burguillos = Reservoir("burguillos", "Embalse de Burguillos", "Burguillos Del Cerro", GeoPoint(38.4044, -6.6030), ReservoirRegime.COTO, coto = "Charco El Toro")
    private val supply = Reservoir("abto", "E. de Abto de Ejemplo", "Ejemplo", GeoPoint(39.0, -6.0), waterSupplyByName = true)
    private val all = listOf(brovales, burguillos, supply)

    @Test
    fun `el mas cercano, por sesion y busqueda sin tildes`() {
        assertEquals(burguillos, Reservoirs.nearest(GeoPoint(38.41, -6.60), all))
        assertNull(Reservoirs.nearest(GeoPoint(39.5, -6.0), all))
        val t = Instant.parse("2026-10-17T08:00:00Z")
        val sessions = listOf(
            Session(id = 1, start = t, location = GeoPoint(38.35, -6.70), createdAt = t),
            Session(id = 2, start = t, location = GeoPoint(38.405, -6.603), createdAt = t),
        )
        assertEquals(listOf(2L), Reservoirs.sessionsAt(sessions, burguillos, all).map { it.id })
        assertEquals(listOf(burguillos), Reservoirs.search(all, "BURGUÍLLOS", null))
        assertEquals(listOf(burguillos), Reservoirs.search(all, "charco el toro", null))
        assertEquals(burguillos, Reservoirs.search(all, "", GeoPoint(38.41, -6.6)).first())
    }

    @Test
    fun `se puede cebar - el usuario manda y el abastecimiento por nombre prohibe`() {
        assertNull(ReservoirContext(burguillos, null).groundbaitAllowed)
        assertFalse(ReservoirContext(burguillos, null).groundbaitDoubt)
        assertEquals(false, ReservoirContext(supply, null).groundbaitAllowed)
        assertTrue(ReservoirContext(burguillos, ReservoirSheet("burguillos", aiWaterSupply = true)).groundbaitDoubt)
        assertEquals(true, ReservoirContext(supply, ReservoirSheet("abto", groundbaitAllowed = true)).groundbaitAllowed)
        assertTrue(ReservoirContext(brovales, null).isBrovales)
    }

    @Test
    fun `especies que cambian la estrategia`() {
        val e = SpeciesRules.effects(setOf(FishSpecies.BARBEL, FishSpecies.CRAYFISH, FishSpecies.CATFISH))
        assertTrue(e.selective && e.hardBaits && e.lessParticles && e.strongRig)
        assertEquals(listOf(FishSpecies.BARBEL, FishSpecies.CRAYFISH, FishSpecies.CATFISH), e.notes.map { it.first })
        assertEquals(SpeciesEffects(), SpeciesRules.effects(setOf(FishSpecies.CARP)))
    }

    @Test
    fun `con barbo y cangrejo los cebos son duros y selectivos, y sin cebado si no se puede`() {
        val point = GeoPoint(38.40, -6.60)
        val sun = SolarCalculator.sunTimes(LocalDate.of(2026, 10, 17), point)
        val gear = listOf(
            GearItem("1", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
            GearItem("2", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT),
            GearItem("3", GearCategory.BAIT, "Boilie 20", baitType = BaitType.BOILIE),
        )
        val sheet = ReservoirSheet("burguillos", mapOf(FishSpecies.BARBEL to SpeciesStatus(true, true), FishSpecies.CRAYFISH to SpeciesStatus(true, false)), groundbaitAllowed = false)
        val input = AdvisorInput(sun.sunrise!!.plusSeconds(3600), FishingPhase.AUTUMN, 17.0, sun, LegalWindow.of(sun), gear = gear, reservoir = ReservoirContext(burguillos, sheet))
        val a = FishingAdvisor.advise(input)
        assertEquals(listOf("Chufa", "Boilie 20"), a.baits.map { it.gearName })
        assertNull(a.groundbait)
        assertTrue(a.species.hardBaits)
        val free = FishingAdvisor.advise(input.copy(reservoir = null))
        assertEquals(GroundbaitLevel.NORMAL, free.groundbait)
        assertEquals("Boilie 20", free.baits.first().gearName)
    }

    @Test
    fun `lo que confirma el usuario en la ficha`() {
        val t = Instant.parse("2026-10-17T10:00:00Z")
        val ai = ReservoirSheet("x", mapOf(FishSpecies.BARBEL to SpeciesStatus(true, false), FishSpecies.PIKE to SpeciesStatus(true, false)))
        val noPike = ReservoirSheets.setSpecies(ai, "x", FishSpecies.PIKE, false, t)
        assertEquals(SpeciesStatus(false, true), noPike.species[FishSpecies.PIKE])
        assertEquals(setOf(FishSpecies.BARBEL), noPike.presentSpecies)
        assertTrue(noPike.hasUnconfirmed)
        assertFalse(ReservoirSheets.confirmAll(noPike, t).hasUnconfirmed)
        assertFalse(FishSpecies.PIKE in ReservoirSheets.setSpecies(noPike, "x", FishSpecies.PIKE, null, t).species)
        val fresh = ReservoirSheets.setGroundbait(null, "x", false, t)
        assertEquals(false, fresh.groundbaitAllowed)
        assertEquals(t, fresh.updatedAt)
    }

    @Test
    fun `buscar sin tildes`() {
        val list = listOf(Reservoir("a", "Embalse de Cáceres", point = GeoPoint(39.4, -6.4)), Reservoir("b", "Embalse de Brovales", point = GeoPoint(38.35, -6.7)))
        assertEquals(listOf("a"), Reservoirs.search(list, "caceres", null).map { it.id })
    }

    @Test
    fun `el embalse de un puesto sale de su punto del mapa`() {
        assertEquals("burguillos", Reservoirs.forSpot(GeoPoint(38.405, -6.604), all, Reservoirs.BROVALES_ID))
        // Sin punto, o lejos de cualquier embalse del catálogo: el activo.
        assertEquals(Reservoirs.BROVALES_ID, Reservoirs.forSpot(null, all, Reservoirs.BROVALES_ID))
        assertEquals("propio-1", Reservoirs.forSpot(GeoPoint(39.5, -6.0), all, "propio-1"))
    }
}
