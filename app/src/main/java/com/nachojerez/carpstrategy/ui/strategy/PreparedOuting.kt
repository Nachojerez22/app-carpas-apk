package com.nachojerez.carpstrategy.ui.strategy

import com.nachojerez.carpstrategy.data.userdata.JournalJson
import com.nachojerez.carpstrategy.domain.advice.Contingency
import com.nachojerez.carpstrategy.domain.advice.ContingencyLine
import com.nachojerez.carpstrategy.domain.advice.DayPlan
import com.nachojerez.carpstrategy.domain.advice.DepthBand
import com.nachojerez.carpstrategy.domain.advice.DistanceBand
import com.nachojerez.carpstrategy.domain.advice.GroundbaitAmount
import com.nachojerez.carpstrategy.domain.advice.PlanSource
import com.nachojerez.carpstrategy.domain.advice.RodLine
import com.nachojerez.carpstrategy.domain.advice.Stat
import com.nachojerez.carpstrategy.domain.advice.WindTip
import com.nachojerez.carpstrategy.domain.assistant.AiAdvice
import com.nachojerez.carpstrategy.domain.assistant.AiGroundbait
import com.nachojerez.carpstrategy.domain.assistant.AiMixPart
import com.nachojerez.carpstrategy.domain.derived.WaterTempSource
import com.nachojerez.carpstrategy.domain.guided.BaitType
import com.nachojerez.carpstrategy.domain.guided.Column
import com.nachojerez.carpstrategy.domain.guided.GroundbaitLevel
import com.nachojerez.carpstrategy.domain.guided.Spot
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.guided.RodKind
import com.nachojerez.carpstrategy.domain.guided.RodSetup
import com.nachojerez.carpstrategy.domain.guided.StepKind
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import com.nachojerez.carpstrategy.domain.rules.StrategyField
import com.nachojerez.carpstrategy.domain.rules.TimeWindow
import com.nachojerez.carpstrategy.domain.rules.WindowKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Ver la preparación (fase 7.19): al pulsar «Preparar la próxima salida» se guarda una copia de
 * lo que salió (condiciones a esa hora, plan por caña, engodo, «si cambia algo» y cómo fue cada
 * paso) para poder abrirla y analizarla después, aunque se cierre la app o no haya red. Es una
 * foto de ese momento: no se recalcula con la previsión de después.
 */

/** Ajuste local (no se sincroniza) con la última preparación en JSON ([PreparedOutingJson]). */
const val KEY_PREPARED_OUTING = "strategy_prepared_outing"

/** Las condiciones a la hora de la salida, como las vio la app al preparar. */
data class PreparedConditions(
    val blocked: Boolean,
    val band: FavorabilityBand?,
    val limitingLevel: RuleLevel?,
    /** Valor (0–1) de cada nivel de la cadena: dónde se queda corta la salida. */
    val levels: List<Pair<RuleLevel, Double>> = emptyList(),
    val waterC: Double? = null,
    val waterSource: WaterTempSource? = null,
    val airC: Double? = null,
    val windKmh: Double? = null,
    val windFromDeg: Double? = null,
    val gustKmh: Double? = null,
    /** Ventanas de ese día, ya recortadas al horario legal. */
    val windows: List<TimeWindow> = emptyList(),
)

data class PreparedOuting(
    val preparedAt: Instant,
    /** Hora de la salida para la que se preparó ([nextOuting]). */
    val target: Instant,
    val reservoirName: String?,
    val spotName: String?,
    /** El puesto lo eligió la app o la IA (automático), no tú. */
    val spotAuto: Boolean,
    val rods: Int,
    val conditions: PreparedConditions?,
    /** Plan por caña; null si no había datos para planificar o la salida no es legal. */
    val rodLines: List<RodLine> = emptyList(),
    val planSource: PlanSource? = null,
    val aiSummary: String? = null,
    val groundbait: GroundbaitLevel? = null,
    val groundbaitAmount: GroundbaitAmount? = null,
    /** Sin saber si se puede cebar en ese embalse: la app avisa de confirmarlo. */
    val groundbaitDoubt: Boolean = false,
    val aiGroundbait: AiGroundbait? = null,
    val aiTips: List<AiAdvice> = emptyList(),
    val contingencies: List<ContingencyLine> = emptyList(),
    val result: PrepResult,
    /** Embalse y puesto exactos (también el automático) para activarla en el puesto (fase 7.21). */
    val reservoirId: String? = null,
    val spotId: Long? = null,
) {
    val hasPlan: Boolean get() = rodLines.isNotEmpty()
}

/**
 * La foto de la preparación a partir del estado evaluado a la hora de la salida. Función pura.
 * [aiTips]: consejos de la IA (sin los de «dónde», que ya van en el puesto).
 */
fun preparedOutingOf(
    state: StrategyUiState,
    dayPlan: DayPlan?,
    aiTips: List<AiAdvice>,
    result: PrepResult,
    preparedAt: Instant,
): PreparedOuting {
    val conditions = state.result?.let { r ->
        val d = state.derived
        PreparedConditions(
            blocked = r.blocked,
            band = r.band,
            limitingLevel = r.limitingLevel,
            levels = r.levels.map { it.level to it.value }.sortedBy { it.first.order },
            waterC = d?.water?.valueC,
            waterSource = d?.water?.source,
            airC = d?.airTrend?.last24hMeanC,
            windKmh = d?.wind24h?.meanSpeedKmh,
            windFromDeg = d?.wind24h?.dominantDirectionDeg,
            gustKmh = d?.wind24h?.maxGustKmh,
            windows = state.windows,
        )
    }
    val plan = dayPlan?.takeIf { conditions?.blocked == false }
    return PreparedOuting(
        preparedAt = preparedAt,
        target = result.target,
        reservoirName = state.reservoir?.reservoir?.name,
        spotName = plan?.spot?.spot?.name,
        spotAuto = state.chosenSpotId == null,
        rods = state.planRods,
        conditions = conditions,
        rodLines = plan?.rods.orEmpty(),
        planSource = plan?.source,
        aiSummary = plan?.aiSummary,
        groundbait = plan?.groundbait,
        groundbaitAmount = plan?.groundbaitAmount,
        groundbaitDoubt = plan != null && state.advice?.groundbaitDoubt == true,
        aiGroundbait = plan?.aiGroundbait,
        aiTips = if (plan == null) emptyList() else aiTips.filter { it.field != StrategyField.WHERE }.take(MAX_AI_TIPS),
        contingencies = plan?.contingencies.orEmpty(),
        result = result,
        reservoirId = state.reservoir?.reservoir?.id,
        spotId = plan?.spot?.spot?.id,
    )
}

/*
 * Activar la salida preparada (fase 7.21): al día siguiente, ya en el puesto, la sesión guiada
 * empieza en un toque con lo que se preparó (cañas, puesto, cebado y plan de la IA), sin red.
 */

/**
 * La preparación se puede activar ahora: tiene plan, a esa hora no había filtro duro, es de hoy,
 * del embalse en el que estás y aún no ha acabado el horario legal ([legalEnd]; null si no se sabe).
 * Las preparaciones anteriores a la fase 7.21 no guardan el id del embalse: se comparan por nombre.
 */
fun PreparedOuting.activatableAt(now: Instant, zone: ZoneId, reservoirId: String?, reservoirName: String?, legalEnd: Instant?): Boolean {
    if (!hasPlan || conditions?.blocked != false) return false
    if (target.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) return false
    if (legalEnd != null && !now.isBefore(legalEnd)) return false
    return if (this.reservoirId != null) this.reservoirId == reservoirId else this.reservoirName == reservoirName
}

/** Con qué empieza la sesión guiada al activar la preparación. */
data class PreparedStart(
    /** Cañas del plan (de Mi equipo) o, sin cañas apuntadas, tantas como se prepararon (sin nombre). */
    val rodNames: List<String>,
    /** Las del plan, para que la guiada use su tipo y longitud; vacío sin cañas de Mi equipo. */
    val planRods: List<String>,
    val spot: Spot?,
    val zone: FishingZone?,
    val groundbait: GroundbaitLevel?,
)

/** Función pura: el puesto se busca por id y, si se borró y se volvió a crear, por nombre. */
fun preparedStartOf(p: PreparedOuting, spots: List<Spot>): PreparedStart {
    val spot = p.spotId?.let { id -> spots.firstOrNull { it.id == id } }
        ?: p.spotName?.let { n -> spots.firstOrNull { it.name == n && (p.reservoirId == null || it.reservoirId == null || it.reservoirId == p.reservoirId) } }
    val planRods = p.rodLines.mapNotNull { it.setup?.name?.takeIf(String::isNotBlank) }.distinct()
    val count = p.rodLines.size.coerceAtLeast(1)
    return PreparedStart(
        rodNames = planRods.ifEmpty { List(count) { "" } },
        planRods = planRods,
        spot = spot,
        zone = spot?.zone,
        groundbait = p.groundbait,
    )
}

/** Consejos de la IA que se muestran con el plan (los mismos que en Estrategia). */
const val MAX_AI_TIPS = 3

/**
 * JSON de [PreparedOuting] en el ajuste [KEY_PREPARED_OUTING]. Los enums van por nombre y se leen
 * con tolerancia: un valor que ya no existe se queda en null en vez de perder la preparación.
 */
object PreparedOutingJson {
    @Serializable
    data class Dto(
        @SerialName("preparada") val preparedAt: String,
        @SerialName("salida") val target: String,
        @SerialName("embalse") val reservoirName: String? = null,
        @SerialName("puesto") val spotName: String? = null,
        @SerialName("puesto_automatico") val spotAuto: Boolean = true,
        @SerialName("canas") val rods: Int = 0,
        @SerialName("condiciones") val conditions: ConditionsDto? = null,
        @SerialName("plan") val rodLines: List<RodDto> = emptyList(),
        @SerialName("origen_plan") val planSource: String? = null,
        @SerialName("resumen_ia") val aiSummary: String? = null,
        @SerialName("cebado") val groundbait: String? = null,
        @SerialName("cebado_kg") val groundbaitAmount: AmountDto? = null,
        @SerialName("cebado_dudoso") val groundbaitDoubt: Boolean = false,
        @SerialName("engodo_ia") val aiGroundbait: MixDto? = null,
        @SerialName("consejos_ia") val aiTips: List<TipDto> = emptyList(),
        @SerialName("si_cambia") val contingencies: List<ContingencyDto> = emptyList(),
        @SerialName("resultado") val result: ResultDto,
        @SerialName("embalse_id") val reservoirId: String? = null,
        @SerialName("puesto_id") val spotId: Long? = null,
    )

    @Serializable
    data class ConditionsDto(
        @SerialName("bloqueada") val blocked: Boolean = false,
        @SerialName("escala") val band: String? = null,
        @SerialName("nivel_limitante") val limitingLevel: String? = null,
        @SerialName("niveles") val levels: Map<String, Double> = emptyMap(),
        @SerialName("agua_c") val waterC: Double? = null,
        @SerialName("agua_fuente") val waterSource: String? = null,
        @SerialName("aire_c") val airC: Double? = null,
        @SerialName("viento_kmh") val windKmh: Double? = null,
        @SerialName("viento_de_grados") val windFromDeg: Double? = null,
        @SerialName("rachas_kmh") val gustKmh: Double? = null,
        @SerialName("ventanas") val windows: List<WindowDto> = emptyList(),
    )

    @Serializable
    data class WindowDto(
        @SerialName("inicio") val start: String,
        @SerialName("fin") val end: String,
        @SerialName("tipo") val kind: String,
        @SerialName("evidencia") val evidence: String,
    )

    @Serializable
    data class RodDto(
        @SerialName("cana") val rod: Int,
        @SerialName("puesto") val spotName: String? = null,
        @SerialName("tipo_cebo") val baitType: String? = null,
        @SerialName("cebo") val baitName: String? = null,
        @SerialName("montaje") val rigName: String? = null,
        @SerialName("columna") val column: String,
        @SerialName("distancia_m") val distanceM: Double? = null,
        @SerialName("distancia") val distance: String,
        @SerialName("profundidad") val depth: String,
        @SerialName("probado") val proven: ProvenDto? = null,
        @SerialName("porque") val reason: String? = null,
        @SerialName("evidencia") val evidence: String,
        @SerialName("origen") val source: String,
        /** Caña de Mi equipo (fase 7.20): nombre, tipo y longitud; peso del flotador o del plomo de la IA. */
        @SerialName("nombre_cana") val rodName: String? = null,
        @SerialName("tipo_cana") val rodKind: String? = null,
        @SerialName("longitud_m") val rodLengthM: Int? = null,
        @SerialName("peso_g") val weightG: Double? = null,
    )

    @Serializable
    data class ProvenDto(
        @SerialName("valor") val value: String,
        @SerialName("horas_cana") val rodHours: Double,
        @SerialName("capturas") val catches: Int,
        @SerialName("sesiones") val sessions: Int,
    )

    @Serializable
    data class AmountDto(
        @SerialName("nivel") val level: String,
        @SerialName("por_cana_kg") val perRodKg: Double,
        @SerialName("total_kg") val totalKg: Double,
        @SerialName("al_empezar_kg") val initialPerRodKg: Double,
        @SerialName("recebado_kg") val rebaitPerRodKg: Double,
    )

    @Serializable
    data class MixDto(
        @SerialName("mezcla") val mix: List<PartDto> = emptyList(),
        @SerialName("kg_por_cana") val perRodKg: Double? = null,
        @SerialName("recebado") val rebait: String = "",
        @SerialName("porque") val reason: String = "",
        @SerialName("evidencia") val evidence: String? = null,
    )

    @Serializable
    data class PartDto(@SerialName("cebo") val baitName: String, @SerialName("partes") val parts: Int)

    @Serializable
    data class TipDto(
        @SerialName("campo") val field: String? = null,
        @SerialName("texto") val text: String,
        @SerialName("evidencia") val evidence: String? = null,
    )

    @Serializable
    data class ContingencyDto(
        @SerialName("tipo") val kind: String,
        @SerialName("tras_min") val afterMin: Long? = null,
        @SerialName("paso") val step: String? = null,
        @SerialName("viento") val windTip: String? = null,
        @SerialName("evidencia") val evidence: String,
    )

    @Serializable
    data class ResultDto(
        @SerialName("prevision_ok") val forecastOk: Boolean,
        @SerialName("plan_ia") val plan: String,
        @SerialName("detalle_ia") val planDetail: String? = null,
        @SerialName("fichas_mejoradas") val sheetsImproved: Int = 0,
        @SerialName("fichas_pendientes") val sheetsPending: Int = 0,
    )

    fun encode(p: PreparedOuting): String = JournalJson.json.encodeToString(Dto.serializer(), p.toDto())

    fun decode(text: String?): PreparedOuting? =
        text?.let { runCatching { JournalJson.json.decodeFromString(Dto.serializer(), it).toDomain() }.getOrNull() }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private fun PreparedOuting.toDto() = Dto(
        preparedAt = preparedAt.toString(),
        target = target.toString(),
        reservoirName = reservoirName,
        spotName = spotName,
        spotAuto = spotAuto,
        rods = rods,
        conditions = conditions?.let { c ->
            ConditionsDto(
                c.blocked, c.band?.name, c.limitingLevel?.name, c.levels.associate { (l, v) -> l.name to v },
                c.waterC, c.waterSource?.name, c.airC, c.windKmh, c.windFromDeg, c.gustKmh,
                c.windows.map { WindowDto(it.start.toString(), it.end.toString(), it.kind.name, it.evidence.name) },
            )
        },
        rodLines = rodLines.map { r ->
            RodDto(
                r.rod, r.spotName, r.baitType?.name, r.baitName, r.rigName, r.column.name, r.distanceM, r.distance.name, r.depth.name,
                r.proven?.let { ProvenDto(it.value, it.rodHours, it.catches, it.sessions) }, r.reason, r.evidence.name, r.source.name,
                r.setup?.name, r.setup?.kind?.name, r.setup?.lengthM, r.weightG,
            )
        },
        planSource = planSource?.name,
        aiSummary = aiSummary,
        groundbait = groundbait?.name,
        groundbaitDoubt = groundbaitDoubt,
        groundbaitAmount = groundbaitAmount?.let { AmountDto(it.level.name, it.perRodKg, it.totalKg, it.initialPerRodKg, it.rebaitPerRodKg) },
        aiGroundbait = aiGroundbait?.let { g -> MixDto(g.mix.map { PartDto(it.baitName, it.parts) }, g.perRodKg, g.rebait, g.reason, g.evidence?.name) },
        aiTips = aiTips.map { TipDto(it.field?.name, it.text, it.evidence?.name) },
        contingencies = contingencies.map { ContingencyDto(it.kind.name, it.after?.toMinutes(), it.step?.name, it.windTip?.name, it.evidence.name) },
        result = ResultDto(result.forecastOk, result.plan.name, result.planDetail, result.sheetsImproved, result.sheetsPending),
        reservoirId = reservoirId,
        spotId = spotId,
    )

    private fun Dto.toDomain(): PreparedOuting {
        val targetAt = Instant.parse(target)
        return PreparedOuting(
            preparedAt = Instant.parse(preparedAt),
            target = targetAt,
            reservoirName = reservoirName,
            spotName = spotName,
            spotAuto = spotAuto,
            rods = rods,
            conditions = conditions?.let { c ->
                PreparedConditions(
                    blocked = c.blocked,
                    band = enumOrNull<FavorabilityBand>(c.band),
                    limitingLevel = enumOrNull<RuleLevel>(c.limitingLevel),
                    levels = c.levels.mapNotNull { (k, v) -> enumOrNull<RuleLevel>(k)?.let { it to v } }.sortedBy { it.first.order },
                    waterC = c.waterC,
                    waterSource = enumOrNull<WaterTempSource>(c.waterSource),
                    airC = c.airC,
                    windKmh = c.windKmh,
                    windFromDeg = c.windFromDeg,
                    gustKmh = c.gustKmh,
                    windows = c.windows.mapNotNull { w ->
                        val kind = enumOrNull<WindowKind>(w.kind) ?: return@mapNotNull null
                        TimeWindow(Instant.parse(w.start), Instant.parse(w.end), kind, enumOrNull<Evidence>(w.evidence) ?: Evidence.PURPLE)
                    },
                )
            },
            rodLines = rodLines.mapNotNull { r ->
                RodLine(
                    rod = r.rod,
                    spotName = r.spotName,
                    baitType = enumOrNull<BaitType>(r.baitType),
                    baitName = r.baitName,
                    rigName = r.rigName,
                    column = enumOrNull<Column>(r.column) ?: return@mapNotNull null,
                    distanceM = r.distanceM,
                    distance = enumOrNull<DistanceBand>(r.distance) ?: DistanceBand.MEDIUM,
                    depth = enumOrNull<DepthBand>(r.depth) ?: DepthBand.MEDIUM,
                    proven = r.proven?.let { Stat(it.value, it.rodHours, it.catches, it.sessions) },
                    reason = r.reason,
                    evidence = enumOrNull<Evidence>(r.evidence) ?: Evidence.PURPLE,
                    source = enumOrNull<PlanSource>(r.source) ?: PlanSource.APP,
                    setup = if (r.rodName != null || r.rodKind != null) RodSetup(r.rodName.orEmpty(), enumOrNull<RodKind>(r.rodKind), r.rodLengthM) else null,
                    weightG = r.weightG,
                )
            },
            planSource = enumOrNull<PlanSource>(planSource),
            aiSummary = aiSummary,
            groundbait = enumOrNull<GroundbaitLevel>(groundbait),
            groundbaitAmount = groundbaitAmount?.let { a ->
                enumOrNull<GroundbaitLevel>(a.level)?.let { GroundbaitAmount(it, a.perRodKg, a.totalKg, a.initialPerRodKg, a.rebaitPerRodKg) }
            },
            groundbaitDoubt = groundbaitDoubt,
            aiGroundbait = aiGroundbait?.let { g ->
                AiGroundbait(g.mix.map { AiMixPart(it.baitName, it.parts) }, g.perRodKg, g.rebait, g.reason, enumOrNull<Evidence>(g.evidence))
            },
            aiTips = aiTips.map { AiAdvice(enumOrNull<StrategyField>(it.field), it.text, enumOrNull<Evidence>(it.evidence)) },
            contingencies = contingencies.mapNotNull { c ->
                ContingencyLine(
                    kind = enumOrNull<Contingency>(c.kind) ?: return@mapNotNull null,
                    after = c.afterMin?.let { Duration.ofMinutes(it) },
                    step = enumOrNull<StepKind>(c.step),
                    windTip = enumOrNull<WindTip>(c.windTip),
                    evidence = enumOrNull<Evidence>(c.evidence) ?: Evidence.PURPLE,
                )
            },
            result = PrepResult(
                target = targetAt,
                forecastOk = result.forecastOk,
                plan = enumOrNull<PrepPlan>(result.plan) ?: PrepPlan.NOTHING,
                planDetail = result.planDetail,
                sheetsImproved = result.sheetsImproved,
                sheetsPending = result.sheetsPending,
            ),
            reservoirId = reservoirId,
            spotId = spotId,
        )
    }
}
