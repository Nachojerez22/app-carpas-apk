package com.nachojerez.carpstrategy.domain.advice

import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonsTest {
    private val t = Instant.parse("2026-10-12T10:00:00Z")
    private fun lesson(id: String, text: String, status: LessonStatus = LessonStatus.ACCEPTED, conditions: LessonConditions = LessonConditions()) =
        Lesson(id, t, LessonTopic.BAIT, text, conditions, Evidence.PURPLE, LessonSource.AI, status, listOf(3L), "§5.8", correction = true)

    @Test
    fun `condiciones`() {
        val c = LessonConditions(phase = FishingPhase.AUTUMN, waterMinC = 14.0, waterMaxC = 18.0, dayPart = DayPart.DUSK, spotName = "Punta")
        assertTrue(c.matches(FishingPhase.AUTUMN, 16.0, DayPart.DUSK, listOf("punta ")))
        assertFalse(c.matches(FishingPhase.SUMMER, 16.0, DayPart.DUSK, listOf("Punta")))
        assertFalse(c.matches(FishingPhase.AUTUMN, 19.0, DayPart.DUSK, listOf("Punta")))
        assertFalse(c.matches(FishingPhase.AUTUMN, 16.0, DayPart.DAWN, listOf("Punta")))
        assertFalse(c.matches(FishingPhase.AUTUMN, 16.0, DayPart.DUSK, listOf("Cauce")))
        assertTrue(LessonConditions().matches(null, null, null, emptyList()))
    }

    @Test
    fun `aceptar, no repetir y solo usar las aceptadas`() {
        val a = lesson("a", "Chufa al anochecer", LessonStatus.PROPOSED)
        var list = Lessons.addNew(emptyList(), listOf(a))
        list = Lessons.addNew(list, listOf(lesson("b", "  chufa   al ANOCHECER ")))
        assertEquals(1, list.size)
        assertTrue(Lessons.matching(list, null, null, null, emptyList()).isEmpty())
        list = Lessons.setStatus(list, "a", LessonStatus.ACCEPTED)
        assertEquals(listOf("a"), Lessons.matching(list, null, null, null, emptyList()).map { it.id })
        list = Lessons.upsert(list, list.first().copy(text = "Chufa al anochecer en la punta"))
        assertEquals("Chufa al anochecer en la punta", list.single().text)
    }

    @Test
    fun `markdown para incorporar al documento`() {
        val md = Lessons.toMarkdown(
            listOf(lesson("a", "Chufa al anochecer", conditions = LessonConditions(phase = FishingPhase.AUTUMN)), lesson("b", "No", LessonStatus.REJECTED)),
            ZoneId.of("Europe/Madrid"), t, { it.name }, { c -> c.phase?.name.orEmpty() },
        )
        assertTrue(md.contains("## Conocimiento aprendido en Brovales (exportado 2026-10-12)"))
        assertTrue(md.contains("### BAIT"))
        assertTrue(md.contains("- 🟣 Chufa al anochecer — _AUTUMN_ (IA, 2026-10-12 · sesiones 3 · **corrige §5.8**)"))
        assertFalse(md.contains("- 🟣 No"))
    }
}
