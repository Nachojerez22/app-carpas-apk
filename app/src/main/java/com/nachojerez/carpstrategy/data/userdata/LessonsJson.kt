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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** JSON del conocimiento aprendido (ajuste `lessons`, se sincroniza). Enums por nombre. */
object LessonsJson {
    @Serializable
    data class ConditionsDto(
        @SerialName("fase") val phase: String? = null,
        @SerialName("agua_min_c") val waterMinC: Double? = null,
        @SerialName("agua_max_c") val waterMaxC: Double? = null,
        @SerialName("momento") val dayPart: String? = null,
        @SerialName("puesto") val spotName: String? = null,
    )

    @Serializable
    data class LessonDto(
        @SerialName("id") val id: String,
        @SerialName("creada") val createdAt: String,
        @SerialName("tema") val topic: String,
        @SerialName("texto") val text: String,
        @SerialName("condiciones") val conditions: ConditionsDto = ConditionsDto(),
        @SerialName("evidencia") val evidence: String = Evidence.PURPLE.name,
        @SerialName("origen") val source: String,
        @SerialName("estado") val status: String,
        @SerialName("sesiones") val sessionIds: List<Long> = emptyList(),
        @SerialName("apartado") val knowledgeRef: String? = null,
        @SerialName("corrige") val correction: Boolean = false,
    )

    private inline fun <reified E : Enum<E>> enumOf(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private val serializer = ListSerializer(LessonDto.serializer())

    fun Lesson.toDto() = LessonDto(
        id, createdAt.toString(), topic.name, text,
        ConditionsDto(conditions.phase?.name, conditions.waterMinC, conditions.waterMaxC, conditions.dayPart?.name, conditions.spotName),
        evidence.name, source.name, status.name, sessionIds, knowledgeRef, correction,
    )

    fun LessonDto.toDomain(): Lesson? = Lesson(
        id = id,
        createdAt = runCatching { Instant.parse(createdAt) }.getOrNull() ?: return null,
        topic = enumOf<LessonTopic>(topic) ?: LessonTopic.OTHER,
        text = text.takeIf { it.isNotBlank() } ?: return null,
        conditions = LessonConditions(
            phase = enumOf<FishingPhase>(conditions.phase),
            waterMinC = conditions.waterMinC,
            waterMaxC = conditions.waterMaxC,
            dayPart = enumOf<DayPart>(conditions.dayPart),
            spotName = conditions.spotName,
        ),
        evidence = enumOf<Evidence>(evidence) ?: Evidence.PURPLE,
        source = enumOf<LessonSource>(source) ?: LessonSource.USER,
        status = enumOf<LessonStatus>(status) ?: LessonStatus.PROPOSED,
        sessionIds = sessionIds,
        knowledgeRef = knowledgeRef,
        correction = correction,
    )

    fun encode(lessons: List<Lesson>): String = JournalJson.json.encodeToString(serializer, lessons.map { it.toDto() })

    fun decode(text: String?): List<Lesson> =
        text?.let { runCatching { JournalJson.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty().mapNotNull { it.toDomain() }
}
