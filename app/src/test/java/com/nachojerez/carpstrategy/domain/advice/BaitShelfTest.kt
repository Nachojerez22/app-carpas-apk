package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BaitShelfTest {
    private val t0 = Instant.parse("2026-10-01T07:00:00Z")
    private val point = GeoPoint(38.35, -6.70)

    private fun session(id: Long, daysAgo: Long, bait: String) = Session(
        id = id, start = t0.minus(Duration.ofDays(daysAgo)), end = t0.minus(Duration.ofDays(daysAgo)).plus(Duration.ofHours(4)),
        location = point, bait = bait, createdAt = t0,
    )

    @Test
    fun `uso del diario por nombre sin tildes y con varios cebos en una sesion`() {
        val usage = BaitShelf.usage(listOf(session(1, 10, "Maíz, Boilie fresa"), session(2, 3, "maiz"), session(3, 1, "Chufa")))
        assertEquals(listOf("Chufa", "maiz", "Boilie fresa"), usage.map { it.name })
        assertEquals(2, usage.first { it.name == "maiz" }.sessions)
    }

    @Test
    fun `hoy llevo, guardados por ultimo uso y los del diario que faltan`() {
        val gear = listOf(
            GearItem("a", GearCategory.BAIT, "Boilie fresa", baitType = BaitType.BOILIE, available = false),
            GearItem("b", GearCategory.BAIT, "Maíz", baitType = BaitType.MAIZE, available = false),
            GearItem("c", GearCategory.BAIT, "Pellet", baitType = BaitType.PELLET, available = true),
            GearItem("d", GearCategory.BAIT, "Zzz nuevo", baitType = BaitType.OTHER, available = false),
            GearItem("r", GearCategory.RIG, "Zig", available = true),
        )
        val usage = BaitShelf.usage(listOf(session(1, 10, "Boilie fresa"), session(2, 3, "maiz"), session(3, 1, "Chufa")))
        val view = BaitShelf.view(gear, usage)
        assertEquals(listOf("Pellet"), view.today.map { it.name })
        assertEquals(listOf("Maíz", "Boilie fresa", "Zzz nuevo"), view.saved.map { it.name })
        assertEquals(listOf("Chufa"), view.fromDiary.map { it.name })
        assertEquals(1, view.usageById.getValue("b").sessions)

        val cleared = BaitShelf.clearToday(gear)
        assertTrue(cleared.filter { it.category == GearCategory.BAIT }.none { it.available })
        assertTrue(cleared.first { it.id == "r" }.available)

        val added = BaitShelf.fromUsage(view.fromDiary.single(), "x")
        assertEquals("Chufa", added.name)
        assertTrue(added.available)
        assertEquals(BaitType.OTHER, added.baitType)
        assertFalse(BaitShelf.view(gear + added, usage).fromDiary.any { it.name == "Chufa" })
    }
}
