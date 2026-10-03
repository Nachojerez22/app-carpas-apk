package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.domain.journal.Catch
import com.nachojerez.carpstrategy.domain.journal.FeatureSnapshot
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.PredictionSnapshot
import com.nachojerez.carpstrategy.domain.journal.Session
import com.nachojerez.carpstrategy.domain.journal.SessionContext
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import com.nachojerez.carpstrategy.domain.rules.FavorabilityBand
import com.nachojerez.carpstrategy.domain.rules.FeedingDemand
import com.nachojerez.carpstrategy.domain.rules.RuleLevel
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * JSON del diario: columnas de [SessionEntity] y formato de exportación `carpstrategy-diario`
 * v1 (docs/FORMATO_DIARIO.md). Los instantes van en ISO-8601 UTC y los enums por nombre.
 */
object JournalJson {
    const val FORMAT = "carpstrategy-diario"
    const val VERSION = 1

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    private val pretty = Json(json) { prettyPrint = true }

    @Serializable
    data class CatchDto(
        @SerialName("hora") val time: String? = null,
        @SerialName("peso_kg") val weightKg: Double? = null,
        @SerialName("cana") val rod: Int? = null,
        @SerialName("especie") val species: String? = null,
    )

    @Serializable
    data class FeaturesDto(
        @SerialName("numeros") val numbers: Map<String, Double> = emptyMap(),
        @SerialName("booleanos") val booleans: Map<String, Boolean> = emptyMap(),
        @SerialName("textos") val texts: Map<String, String> = emptyMap(),
    )

    @Serializable
    data class PredictionDto(
        @SerialName("calculada") val computedAt: String,
        @SerialName("lat") val latitude: Double,
        @SerialName("lon") val longitude: Double,
        @SerialName("bloqueada") val blocked: Boolean,
        @SerialName("favorabilidad") val favorability: Double? = null,
        @SerialName("tramo") val band: String? = null,
        @SerialName("nivel_limitante") val limitingLevel: String? = null,
        @SerialName("demanda") val demand: String? = null,
        @SerialName("parametros") val features: FeaturesDto? = null,
        @SerialName("niveles") val levels: Map<String, Double> = emptyMap(),
        @SerialName("reglas_activas") val activeRules: Map<String, Double> = emptyMap(),
        @SerialName("huella_reglas") val rulesFingerprint: String? = null,
    )

    @Serializable
    data class ContextDto(
        @SerialName("legal_inicio") val legalStart: String? = null,
        @SerialName("legal_fin") val legalEnd: String? = null,
        @SerialName("temp_agua_c") val waterTempC: Double? = null,
        @SerialName("agua_medida") val waterMeasured: Boolean = false,
        @SerialName("temp_aire_24h_c") val airTemp24hC: Double? = null,
        @SerialName("viento_kmh") val windKmh: Double? = null,
        @SerialName("viento_dir_grados") val windDirectionDeg: Double? = null,
        @SerialName("lluvia_72h_mm") val rain72hMm: Double? = null,
        @SerialName("nivel_embalse_pct") val reservoirPercent: Double? = null,
        @SerialName("luna_iluminacion") val moonIllumination: Double? = null,
        @SerialName("presion_hpa") val pressureHpa: Double? = null,
        @SerialName("parametros") val features: FeaturesDto? = null,
    )

    @Serializable
    data class SessionDto(
        @SerialName("inicio") val start: String,
        @SerialName("fin") val end: String? = null,
        @SerialName("lat") val latitude: Double,
        @SerialName("lon") val longitude: Double,
        @SerialName("zona") val zone: String? = null,
        @SerialName("zona_detalle") val zoneDetail: String = "",
        @SerialName("profundidad_m") val depthM: Double? = null,
        @SerialName("canas") val rods: Int,
        @SerialName("horas_cana") val rodHours: Double? = null,
        @SerialName("horas_cana_tecleadas") val rodHoursOverride: Double? = null,
        @SerialName("cebo") val bait: String = "",
        @SerialName("montaje") val rig: String = "",
        @SerialName("cebado_kg") val groundbaitKg: Double? = null,
        @SerialName("otros_pescadores") val otherAnglers: Int? = null,
        @SerialName("picadas") val bites: Int = 0,
        @SerialName("perdidas") val losses: Int = 0,
        @SerialName("capturas") val catches: List<CatchDto> = emptyList(),
        @SerialName("bolo") val blank: Boolean? = null,
        @SerialName("valoracion_previa") val prediction: PredictionDto? = null,
        @SerialName("contexto") val context: ContextDto? = null,
        @SerialName("se_cumplio") val fulfilled: String? = null,
        @SerialName("notas") val notes: String = "",
        @SerialName("creada") val createdAt: String,
        @SerialName("guiado") val guided: GuidedJson.GuidedDto? = null,
    )

    @Serializable
    data class ExportDto(
        @SerialName("formato") val format: String = FORMAT,
        @SerialName("version") val version: Int = VERSION,
        @SerialName("exportado") val exportedAt: String,
        @SerialName("sesiones") val sessions: List<SessionDto>,
    )

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

    private fun instant(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun Catch.toDto() = CatchDto(time?.toString(), weightKg, rod, species)
    fun CatchDto.toDomain() = Catch(instant(time), weightKg, rod, species?.takeIf { it.isNotBlank() })

    fun FeatureSnapshot.toDto() = FeaturesDto(numbers, booleans, texts)
    fun FeaturesDto.toDomain() = FeatureSnapshot(numbers, booleans, texts)

    fun PredictionSnapshot.toDto() = PredictionDto(
        computedAt.toString(), location.latitude, location.longitude, blocked, favorability, band?.name, limitingLevel?.name, demand?.name,
        features?.toDto(), levels.mapKeys { it.key.name }, activeRules, rulesFingerprint,
    )

    fun PredictionDto.toDomain(): PredictionSnapshot? = PredictionSnapshot(
        computedAt = instant(computedAt) ?: return null,
        location = GeoPoint(latitude, longitude),
        blocked = blocked,
        favorability = favorability,
        band = enumOrNull<FavorabilityBand>(band),
        limitingLevel = enumOrNull<RuleLevel>(limitingLevel),
        demand = enumOrNull<FeedingDemand>(demand),
        features = features?.toDomain(),
        levels = levels.mapNotNull { (k, v) -> enumOrNull<RuleLevel>(k)?.let { it to v } }.toMap(),
        activeRules = activeRules,
        rulesFingerprint = rulesFingerprint,
    )

    fun SessionContext.toDto() = ContextDto(
        legalStart?.toString(), legalEnd?.toString(), waterTempC, waterMeasured, airTemp24hC, windKmh, windDirectionDeg,
        rain72hMm, reservoirPercent, moonIllumination, pressureHpa, features?.toDto(),
    )

    fun ContextDto.toDomain() = SessionContext(
        instant(legalStart), instant(legalEnd), waterTempC, waterMeasured, airTemp24hC, windKmh, windDirectionDeg,
        rain72hMm, reservoirPercent, moonIllumination, pressureHpa, features?.toDomain(),
    )

    fun encodeCatches(catches: List<Catch>): String = json.encodeToString(catches.map { it.toDto() })

    fun decodeCatches(text: String): List<Catch> =
        runCatching { json.decodeFromString<List<CatchDto>>(text) }.getOrDefault(emptyList()).map { it.toDomain() }

    fun encodePrediction(snapshot: PredictionSnapshot): String = json.encodeToString(snapshot.toDto())

    fun decodePrediction(text: String?): PredictionSnapshot? =
        text?.let { runCatching { json.decodeFromString<PredictionDto>(it) }.getOrNull() }?.toDomain()

    fun encodeContext(context: SessionContext): String = json.encodeToString(context.toDto())

    fun decodeContext(text: String?): SessionContext? =
        text?.let { runCatching { json.decodeFromString<ContextDto>(it) }.getOrNull() }?.toDomain()

    fun Session.toDto() = SessionDto(
        start = start.toString(),
        end = end?.toString(),
        latitude = location.latitude,
        longitude = location.longitude,
        zone = zone?.name,
        zoneDetail = zoneDetail,
        depthM = depthM,
        rods = rods,
        rodHours = rodHours,
        rodHoursOverride = rodHoursOverride,
        bait = bait,
        rig = rig,
        groundbaitKg = groundbaitKg,
        otherAnglers = otherAnglers,
        bites = bites,
        losses = losses,
        catches = catches.map { it.toDto() },
        blank = blank,
        prediction = prediction?.toDto(),
        context = context?.toDto(),
        fulfilled = fulfilled?.name,
        notes = notes,
        createdAt = createdAt.toString(),
        guided = guided?.let { with(GuidedJson) { it.toDto() } },
    )

    fun SessionDto.toDomain(): Session? = Session(
        start = instant(start) ?: return null,
        end = instant(end),
        location = GeoPoint(latitude, longitude),
        zone = enumOrNull<FishingZone>(zone),
        zoneDetail = zoneDetail,
        depthM = depthM,
        rods = rods,
        rodHoursOverride = rodHoursOverride,
        bait = bait,
        rig = rig,
        groundbaitKg = groundbaitKg,
        otherAnglers = otherAnglers,
        bites = bites,
        losses = losses,
        catches = catches.map { it.toDomain() },
        blank = blank,
        prediction = prediction?.toDomain(),
        context = context?.toDomain(),
        fulfilled = enumOrNull<Fulfilled>(fulfilled),
        notes = notes,
        createdAt = instant(createdAt) ?: Instant.EPOCH,
        guided = guided?.let { with(GuidedJson) { it.toDomain() } },
    )

    /** Copia de seguridad legible del diario (la app no usa la copia en la nube de Android). */
    fun export(sessions: List<Session>, now: Instant): String =
        pretty.encodeToString(ExportDto(exportedAt = now.toString(), sessions = sessions.sortedBy { it.start }.map { it.toDto() }))

    /** Sesiones de [incoming] que no están ya en [existing] (misma hora de inicio). */
    fun newSessions(existing: List<Session>, incoming: List<Session>): List<Session> {
        val starts = existing.map { it.start }.toMutableSet()
        return incoming.filter { starts.add(it.start) }
    }

    /** Lee una exportación. Null si no es un archivo `carpstrategy-diario` válido. */
    fun parseExport(text: String): List<Session>? {
        val dto = runCatching { json.decodeFromString<ExportDto>(text) }.getOrNull() ?: return null
        if (dto.format != FORMAT || dto.version != VERSION) return null
        return dto.sessions.mapNotNull { it.toDomain() }
    }
}
