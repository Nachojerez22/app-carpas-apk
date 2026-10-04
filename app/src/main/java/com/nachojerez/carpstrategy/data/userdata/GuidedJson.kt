package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.ConditionChange
import com.nachojerez.carpstrategy.domain.guided.Decision
import com.nachojerez.carpstrategy.domain.guided.FieldCondition
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.GuidedRecord
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.ProposalRecord
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.RodTrack
import com.nachojerez.carpstrategy.domain.guided.Segment
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
import com.nachojerez.carpstrategy.domain.guided.Species
import com.nachojerez.carpstrategy.domain.guided.Situation
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * JSON de la sesión guiada (columna `guidedJson` de [SessionEntity] y campo `guiado` de la
 * exportación del diario) y del equipo del usuario (ajuste `gear`). Enums por nombre;
 * los valores desconocidos se ignoran al leer.
 */
object GuidedJson {
    @Serializable
    data class CheckInDto(
        @SerialName("hora") val time: String,
        @SerialName("senales") val signals: String,
        @SerialName("actividad") val activity: String,
        @SerialName("estado_cebo") val baitState: String,
        @SerialName("no_funciona") val notWorking: Boolean = false,
        @SerialName("cambio_usuario") val userChange: String? = null,
        @SerialName("especie") val species: String? = null,
        @SerialName("recebado") val rebait: String? = null,
    )

    @Serializable
    data class ProposalDto(
        @SerialName("paso") val kind: String,
        @SerialName("situacion") val situation: String,
        @SerialName("cebo") val bait: String? = null,
        @SerialName("cebo_nombre") val baitName: String? = null,
        @SerialName("cebo_sin_equivalente") val baitFallback: Boolean = false,
        @SerialName("columna") val column: String? = null,
        @SerialName("montaje") val rigName: String? = null,
        @SerialName("forzada") val forced: Boolean = false,
        @SerialName("evidencia") val evidence: String,
        @SerialName("creada") val createdAt: String,
    )

    @Serializable
    data class ProposalRecordDto(
        @SerialName("propuesta") val proposal: ProposalDto,
        @SerialName("decision") val decision: String,
        @SerialName("motivo") val reason: String? = null,
        @SerialName("comentario") val comment: String = "",
        @SerialName("decidida") val decidedAt: String? = null,
    )

    @Serializable
    data class SegmentDto(
        @SerialName("inicio") val start: String,
        @SerialName("fin") val end: String? = null,
        @SerialName("paso") val kind: String,
        @SerialName("cebo") val bait: String? = null,
        @SerialName("cebo_nombre") val baitName: String? = null,
        @SerialName("columna") val column: String? = null,
        @SerialName("forzado") val forced: Boolean = false,
        @SerialName("avisos") val checkIns: List<CheckInDto> = emptyList(),
        @SerialName("montaje") val rigName: String? = null,
    )

    @Serializable
    data class RodDto(
        @SerialName("id") val id: Int,
        @SerialName("nombre") val name: String = "",
        @SerialName("tramos") val segments: List<SegmentDto>,
        @SerialName("propuestas") val proposals: List<ProposalRecordDto> = emptyList(),
    )

    @Serializable
    data class ConditionDto(
        @SerialName("hora") val time: String,
        @SerialName("tipo") val condition: String,
        @SerialName("activa") val active: Boolean,
    )

    /** `tramos` y `propuestas` sueltos: formato de prueba anterior a las cañas (una sola caña). */
    @Serializable
    data class GuidedDto(
        @SerialName("canas") val rods: List<RodDto> = emptyList(),
        @SerialName("avisos_mostrados") val alarms: List<String> = emptyList(),
        @SerialName("cambios_viento") val windChanges: List<String> = emptyList(),
        @SerialName("cebado_inicial") val groundbait: String? = null,
        @SerialName("condiciones") val conditions: List<ConditionDto> = emptyList(),
        @SerialName("tramos") val legacySegments: List<SegmentDto>? = null,
        @SerialName("propuestas") val legacyProposals: List<ProposalRecordDto>? = null,
    )

    @Serializable
    data class GearDto(
        @SerialName("id") val id: String,
        @SerialName("categoria") val category: String,
        @SerialName("nombre") val name: String,
        @SerialName("tipo_cebo") val baitType: String? = null,
        @SerialName("tipo_montaje") val rigType: String? = null,
    )

    private inline fun <reified E : Enum<E>> enumOf(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private fun instant(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun CheckIn.toDto() = CheckInDto(time.toString(), signals.name, activity.name, baitState.name, notWorking, userChange?.name, species?.name, rebait?.name)

    fun CheckInDto.toDomain(): CheckIn? = CheckIn(
        time = instant(time) ?: return null,
        signals = enumOf<SignalLevel>(signals) ?: SignalLevel.NONE,
        activity = enumOf<HookActivity>(activity) ?: HookActivity.NOTHING,
        baitState = enumOf<BaitState>(baitState) ?: BaitState.NOT_CHECKED,
        notWorking = notWorking,
        userChange = enumOf<ChangedVariable>(userChange),
        species = enumOf<Species>(species),
        rebait = enumOf<GroundbaitLevel>(rebait),
    )

    fun Proposal.toDto() = ProposalDto(
        kind.name, situation.name, bait?.name, baitName, baitFallback, column?.name, rigName, forced, evidence.name, createdAt.toString(),
    )

    fun ProposalDto.toDomain(): Proposal? = Proposal(
        kind = enumOf<StepKind>(kind) ?: return null,
        situation = enumOf<Situation>(situation) ?: Situation.START,
        bait = enumOf<BaitType>(bait),
        baitName = baitName,
        baitFallback = baitFallback,
        column = enumOf<Column>(column),
        rigName = rigName,
        forced = forced,
        evidence = enumOf<Evidence>(evidence) ?: Evidence.PURPLE,
        createdAt = instant(createdAt) ?: return null,
    )

    private fun Segment.toDto() =
        SegmentDto(start.toString(), end?.toString(), kind.name, bait?.name, baitName, column?.name, forced, checkIns.map { it.toDto() }, rigName)

    private fun SegmentDto.toDomain(): Segment? = Segment(
        start = instant(start) ?: return null,
        end = instant(end),
        kind = enumOf<StepKind>(kind) ?: StepKind.INITIAL,
        bait = enumOf<BaitType>(bait),
        baitName = baitName,
        column = enumOf<Column>(column),
        forced = forced,
        checkIns = checkIns.mapNotNull { it.toDomain() },
        rigName = rigName,
    )

    private fun ProposalRecordDto.toDomain(): ProposalRecord? = ProposalRecord(
        proposal = proposal.toDomain() ?: return null,
        decision = enumOf<Decision>(decision) ?: Decision.PENDING,
        reason = enumOf<RejectReason>(reason),
        comment = comment,
        decidedAt = instant(decidedAt),
    )

    private fun rodLog(segments: List<SegmentDto>, proposals: List<ProposalRecordDto>): GuidedLog? {
        val segs = segments.mapNotNull { it.toDomain() }
        if (segs.isEmpty()) return null
        return GuidedLog(segs, proposals.mapNotNull { it.toDomain() })
    }

    fun GuidedRecord.toDto() = GuidedDto(
        rods = rods.map { r ->
            RodDto(
                id = r.id,
                name = r.name,
                segments = r.log.segments.map { it.toDto() },
                proposals = r.log.proposals.map { p -> ProposalRecordDto(p.proposal.toDto(), p.decision.name, p.reason?.name, p.comment, p.decidedAt?.toString()) },
            )
        },
        alarms = alarms.map { it.toString() },
        windChanges = windChanges.map { it.toString() },
        groundbait = groundbait?.name,
        conditions = conditions.map { ConditionDto(it.time.toString(), it.condition.name, it.active) },
    )

    fun GuidedDto.toDomain(): GuidedRecord? {
        val tracks = rods.mapNotNull { r -> rodLog(r.segments, r.proposals)?.let { RodTrack(r.id, r.name, it) } }
            .ifEmpty { listOfNotNull(legacySegments?.let { rodLog(it, legacyProposals.orEmpty()) }?.let { RodTrack(1, "", it) }) }
        if (tracks.isEmpty()) return null
        return GuidedRecord(
            rods = tracks,
            alarms = alarms.mapNotNull { instant(it) },
            windChanges = windChanges.mapNotNull { instant(it) },
            groundbait = enumOf<GroundbaitLevel>(groundbait),
            conditions = conditions.mapNotNull { c ->
                ConditionChange(instant(c.time) ?: return@mapNotNull null, enumOf<FieldCondition>(c.condition) ?: return@mapNotNull null, c.active)
            },
        )
    }

    fun encode(record: GuidedRecord): String = JournalJson.json.encodeToString(GuidedDto.serializer(), record.toDto())

    fun decode(text: String?): GuidedRecord? =
        text?.let { runCatching { JournalJson.json.decodeFromString(GuidedDto.serializer(), it) }.getOrNull() }?.toDomain()

    fun GearItem.toDto() = GearDto(id, category.name, name, baitType?.name, rigType?.name)

    fun GearDto.toDomain(): GearItem? = GearItem(
        id = id,
        category = enumOf<GearCategory>(category) ?: return null,
        name = name,
        baitType = enumOf<BaitType>(baitType),
        rigType = enumOf<RigType>(rigType),
    )

    fun encodeGear(items: List<GearItem>): String =
        JournalJson.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(GearDto.serializer()), items.map { it.toDto() })

    fun decodeGear(text: String?): List<GearItem> = text?.let {
        runCatching { JournalJson.json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GearDto.serializer()), it) }.getOrNull()
    }.orEmpty().mapNotNull { it.toDomain() }
}
