package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.Lesson
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonSource
import com.nachojerez.carpstrategy.domain.advice.LessonStatus
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.domain.guided.FishingPhase
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonsJsonTest {
    @Test
    fun `ida y vuelta y las invalidas se descartan`() {
        val lessons = listOf(
            Lesson(
                "a", Instant.parse("2026-10-12T10:00:00Z"), LessonTopic.WHERE, "La punta al anochecer",
                LessonConditions(FishingPhase.AUTUMN, 14.0, 18.0, DayPart.DUSK, "Punta"), Evidence.YELLOW,
                LessonSource.AI, LessonStatus.ACCEPTED, listOf(3, 4), "§5.8", correction = true,
            ),
            Lesson("b", Instant.parse("2026-10-12T11:00:00Z"), LessonTopic.OTHER, "Mía", source = LessonSource.USER, status = LessonStatus.ACCEPTED),
        )
        val text = LessonsJson.encode(lessons)
        assertEquals(lessons, LessonsJson.decode(text))
        assertTrue(text.contains("\"corrige\":true"))
        assertEquals(emptyList<Lesson>(), LessonsJson.decode("""[{"id":"x","creada":"mal","tema":"BAIT","texto":"t","origen":"AI","estado":"ACCEPTED"}]"""))
        assertEquals(emptyList<Lesson>(), LessonsJson.decode(null))
    }
}
