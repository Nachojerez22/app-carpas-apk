package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.derived.LegalWindow
import com.nachojerez.carpstrategy.domain.derived.SolarCalculator
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.guided.SpotStructure
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FishingAdvisorTest {
    private val point = GeoPoint(38.35, -6.70)
    private val sun = SolarCalculator.sunTimes(LocalDate.of(2026, 7, 15), point)
    private val legal = LegalWindow.of(sun)
    private val gear = listOf(
        GearItem("1", GearCategory.BAIT, "Boilie fresa", baitType = BaitType.BOILIE),
        GearItem("2", GearCategory.BAIT, "Chufa", baitType = BaitType.TIGERNUT),
        GearItem("3", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE),
    )
    private val spots = listOf(
        Spot(1, "Orilla", structure = SpotStructure.SHALLOW_EDGE, facingDeg = 270, distanceM = 25.0),
        Spot(2, "Caída", structure = SpotStructure.FIRST_DROP, facingDeg = 90, distanceM = 60.0),
    )

    private fun input(now: Instant, phase: FishingPhase = FishingPhase.SUMMER, exp: ExperienceSummary = ExperienceSummary.EMPTY, lessons: List<Lesson> = emptyList()) =
        AdvisorInput(now, phase, 24.0, sun, legal, windFromDeg = 270.0, windKmh = 12.0, spots = spots, gear = gear, experience = exp, lessons = lessons)

    @Test
    fun `verano al amanecer orilla somera cerca y a mediodia la caida lejos`() {
        val dawn = FishingAdvisor.advise(input(sun.sunrise!!.plusSeconds(1800)))
        assertEquals(DayPart.DAWN, dawn.dayPart)
        assertEquals(false, dawn.deferred)
        assertEquals("Orilla", dawn.spots.first().spot.name)
        // Agua a 24 °C (ya en el óptimo): el viento de cara no suma (§5.4, 🟣).
        assertFalse(dawn.spots.first().windBonus)
        assertEquals(DistanceBand.CLOSE, dawn.distance)
        assertEquals(DepthBand.SHALLOW, dawn.depth)
        assertEquals(GroundbaitLevel.HIGH, dawn.groundbait)
        assertEquals(Column.BOTTOM, dawn.column)
        assertEquals(Column.ZIG, dawn.altColumn)

        val noon = sun.sunrise!!.plusSeconds((sun.sunset!!.epochSecond - sun.sunrise!!.epochSecond) / 2)
        val midday = FishingAdvisor.advise(input(noon))
        assertEquals(DayPart.MIDDAY, midday.dayPart)
        assertEquals("Caída", midday.spots.first().spot.name)
        assertEquals(DistanceBand.FAR, midday.distance)
        assertEquals(DepthBand.DEEP, midday.depth)
    }

    @Test
    fun `cebos de la fase con tu equipo, dos distintos para comparar`() {
        val a = FishingAdvisor.advise(input(sun.sunrise!!.plusSeconds(1800)))
        assertEquals(listOf("Boilie fresa", "Maíz"), a.baits.map { it.gearName })
        assertTrue(a.baits.all { it.evidence == Evidence.YELLOW && it.proven == null })
    }

    @Test
    fun `lo que te ha funcionado pasa por delante`() {
        val exp = ExperienceSummary(
            sessions = 3, rodHours = 30.0, catches = 6,
            baits = listOf(Stat("chufa", 10.0, 5, 3), Stat("Boilie fresa", 20.0, 1, 3)),
            baitTypes = emptyList(),
            spots = listOf(Stat("Caída", 10.0, 5, 3)),
            columns = listOf(Stat(Column.POPUP, 10.0, 5, 3)),
            dayParts = listOf(Stat(DayPart.DUSK, 8.0, 4, 3)),
        )
        val a = FishingAdvisor.advise(input(sun.sunrise!!.plusSeconds(1800), exp = exp))
        assertEquals("Chufa", a.baits.first().gearName)
        assertEquals(Evidence.PURPLE, a.baits.first().evidence)
        assertEquals("Caída", a.spots.first().spot.name)
        assertEquals(Column.POPUP, a.column)
        assertTrue(a.columnProven)
        assertEquals(listOf(DayPart.DUSK), a.bestDayParts.map { it.value })
    }

    @Test
    fun `de noche aconseja para el proximo amanecer`() {
        val night = sun.sunset!!.plusSeconds(3 * 3600)
        val a = FishingAdvisor.advise(input(night))
        assertEquals(DayPart.DAWN, a.dayPart)
        assertTrue(a.forTime.isAfter(night))
        assertTrue(a.deferred)
    }

    @Test
    fun `lecciones aceptadas que encajan`() {
        val l1 = Lesson("1", Instant.EPOCH, LessonTopic.WHERE, "En verano al amanecer, la Orilla", LessonConditions(phase = FishingPhase.SUMMER, spotName = "orilla"), source = LessonSource.AI, status = LessonStatus.ACCEPTED)
        val l2 = l1.copy(id = "2", conditions = LessonConditions(phase = FishingPhase.WINTER))
        val l3 = l1.copy(id = "3", status = LessonStatus.PROPOSED)
        val a = FishingAdvisor.advise(input(sun.sunrise!!.plusSeconds(1800), lessons = listOf(l1, l2, l3)))
        assertEquals(listOf("1"), a.lessons.map { it.id })
        assertEquals(4, a.spots.first().score)
    }

    @Test
    fun `invierno caida y cauce, someras solo al mediodia`() {
        assertEquals(3, FishingAdvisor.structureFit(SpotStructure.FIRST_DROP, FishingPhase.WINTER, DayPart.MORNING))
        assertEquals(0, FishingAdvisor.structureFit(SpotStructure.SHALLOW_EDGE, FishingPhase.WINTER, DayPart.MORNING))
        assertEquals(2, FishingAdvisor.structureFit(SpotStructure.SHALLOW_EDGE, FishingPhase.WINTER, DayPart.MIDDAY))
        assertEquals(GroundbaitLevel.LOW, FishingAdvisor.groundbait(FishingPhase.WINTER, DayPart.MIDDAY))
        assertNull(FishingAdvisor.advise(input(sun.sunrise!!.plusSeconds(1800), phase = FishingPhase.WINTER)).altColumn)
    }
}
