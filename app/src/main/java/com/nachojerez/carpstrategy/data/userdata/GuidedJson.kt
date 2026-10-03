package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.guided.BaitState
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.ChangedVariable
import com.nachojerez.carpstrategy.domain.guided.CheckIn
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.Decision
import com.nachojerez.carpstrategy.domain.guided.GearCategory
import com.nachojerez.carpstrategy.domain.guided.GearItem
import com.nachojerez.carpstrategy.domain.guided.GuidedLog
import com.nachojerez.carpstrategy.domain.guided.HookActivity
import com.nachojerez.carpstrategy.domain.guided.Proposal
import com.nachojerez.carpstrategy.domain.guided.ProposalRecord
import com.nachojerez.carpstrategy.domain.guided.RejectReason
import com.nachojerez.carpstrategy.domain.guided.RigType
import com.nachojerez.carpstrategy.domain.guided.Segment
import com.nachojerez.carpstrategy.domain.guided.SignalLevel
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
    )

    @Serializable
    data class GuidedDto(
        @SerialName("tramos") val segments: List<SegmentDto>,
        @SerialName("propuestas") val proposals: List<ProposalRecordDto> = emptyList(),
        @SerialName("avisos_mostrados") val alarms: List<String> = emptyList(),
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

    fun CheckIn.toDto() = CheckInDto(time.toString(), signals.name, activity.name, baitState.name, notWorking, userChange?.name)

    fun CheckInDto.toDomain(): CheckIn? = CheckIn(
        time = instant(time) ?: return null,
        signals = enumOf<SignalLevel>(signals) ?: SignalLevel.NONE,
        activity = enumOf<HookActivity>(activity) ?: HookActivity.NOTHING,
        baitState = enumOf<BaitState>(baitState) ?: BaitState.NOT_CHECKED,
        notWorking = notWorking,
        userChange = enumOf<ChangedVariable>(userChange),
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

    fun GuidedLog.toDto() = GuidedDto(
        segments = segments.map { s ->
            SegmentDto(s.start.toString(), s.end?.toString(), s.kind.name, s.bait?.name, s.baitName, s.column?.name, s.forced, s.checkIns.map { it.toDto() })
        },
        proposals = proposals.map { r -> ProposalRecordDto(r.proposal.toDto(), r.decision.name, r.reason?.name, r.comment, r.decidedAt?.toString()) },
        alarms = alarms.map { it.toString() },
    )

    fun GuidedDto.toDomain(): GuidedLog? {
        val segs = segments.mapNotNull { s ->
            Segment(
                start = instant(s.start) ?: return@mapNotNull null,
                end = instant(s.end),
                kind = enumOf<StepKind>(s.kind) ?: StepKind.INITIAL,
                bait = enumOf<BaitType>(s.bait),
                baitName = s.baitName,
                column = enumOf<Column>(s.column),
                forced = s.forced,
                checkIns = s.checkIns.mapNotNull { it.toDomain() },
            )
        }
        if (segs.isEmpty()) return null
        val records = proposals.mapNotNull { r ->
            ProposalRecord(
                proposal = r.proposal.toDomain() ?: return@mapNotNull null,
                decision = enumOf<Decision>(r.decision) ?: Decision.PENDING,
                reason = enumOf<RejectReason>(r.reason),
                comment = r.comment,
                decidedAt = instant(r.decidedAt),
            )
        }
        return GuidedLog(segs, records, alarms.mapNotNull { instant(it) })
    }

    fun encode(log: GuidedLog): String = JournalJson.json.encodeToString(GuidedDto.serializer(), log.toDto())

    fun decode(text: String?): GuidedLog? =
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
