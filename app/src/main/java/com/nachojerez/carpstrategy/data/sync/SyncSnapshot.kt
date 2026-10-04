package com.nachojerez.carpstrategy.data.sync

import com.nachojerez.carpstrategy.data.userdata.ManualRecordEntity
import com.nachojerez.carpstrategy.data.userdata.PredictionSnapshotEntity
import com.nachojerez.carpstrategy.data.userdata.SessionEntity
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Foto completa de los datos del usuario para guardarla en su Google Drive (formato
 * `carpstrategy-sync` v1). Es la base de datos tal cual, sin los ids locales: cada fila se
 * identifica por su clave natural, que es la misma en cualquier móvil.
 *
 * No incluye los ajustes privados del móvil ([isLocalOnly]): estado de la sincronización, avisos
 * descartados y, más adelante, la clave de la IA.
 */
@Serializable
data class SyncSnapshot(
    @SerialName("registros") val records: List<RecordRow> = emptyList(),
    @SerialName("ajustes") val settings: List<SettingRow> = emptyList(),
    @SerialName("sesiones") val sessions: List<SessionRow> = emptyList(),
    @SerialName("valoraciones") val predictions: List<PredictionRow> = emptyList(),
) {
    val isEmpty: Boolean get() = records.isEmpty() && settings.isEmpty() && sessions.isEmpty() && predictions.isEmpty()

    /** Orden fijo para que la misma información dé siempre la misma huella. */
    fun canonical(): SyncSnapshot = SyncSnapshot(
        records = records.sortedWith(compareBy({ it.locationKey }, { it.periodKey }, { it.source })),
        settings = settings.sortedBy { it.key },
        sessions = sessions.sortedWith(compareBy({ it.createdAtEpochMs }, { it.startEpochSecond })),
        predictions = predictions.sortedWith(compareBy({ it.hourKey }, { it.locationKey })),
    )

    /** Huella SHA-256 del contenido: detecta cambios sin comparar fila a fila. */
    fun hash(): String {
        val bytes = SyncJson.json.encodeToString(serializer(), canonical()).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { ((it.toInt() and 0xff) + 0x100).toString(16).substring(1) }
    }

    companion object {
        /** Ajustes que se quedan en el móvil y nunca se suben. */
        fun isLocalOnly(key: String): Boolean = key.startsWith(LOCAL_PREFIX)

        const val LOCAL_PREFIX = "local_"
    }
}

@Serializable
data class RecordRow(
    @SerialName("periodo") val periodKey: String,
    @SerialName("epoch") val epochSecond: Long? = null,
    @SerialName("fecha") val date: String? = null,
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
    @SerialName("lugar") val locationKey: String,
    @SerialName("fuente") val source: String,
    @SerialName("origen") val origin: String,
    @SerialName("archivo") val originFile: String? = null,
    @SerialName("valores") val valuesJson: String,
    @SerialName("notas") val notes: String? = null,
    @SerialName("creado") val createdAtEpochMs: Long,
) {
    val key: Triple<String, String, String> get() = Triple(locationKey, periodKey, source)
}

@Serializable
data class SettingRow(
    @SerialName("clave") val key: String,
    @SerialName("valor") val value: String,
)

/** Sesión: se identifica por el instante de creación (y el inicio), no por el id local. */
@Serializable
data class SessionRow(
    @SerialName("inicio") val startEpochSecond: Long,
    @SerialName("fin") val endEpochSecond: Long? = null,
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
    @SerialName("zona") val zone: String? = null,
    @SerialName("zona_detalle") val zoneDetail: String = "",
    @SerialName("profundidad") val depthM: Double? = null,
    @SerialName("canas") val rods: Int,
    @SerialName("horas_cana") val rodHoursOverride: Double? = null,
    @SerialName("cebo") val bait: String = "",
    @SerialName("montaje") val rig: String = "",
    @SerialName("engodo_kg") val groundbaitKg: Double? = null,
    @SerialName("otros") val otherAnglers: Int? = null,
    @SerialName("picadas") val bites: Int = 0,
    @SerialName("perdidas") val losses: Int = 0,
    @SerialName("capturas") val catchesJson: String,
    @SerialName("bolo") val blank: Boolean? = null,
    @SerialName("valoracion") val predictionJson: String? = null,
    @SerialName("contexto") val contextJson: String? = null,
    @SerialName("se_cumplio") val fulfilled: String? = null,
    @SerialName("notas") val notes: String = "",
    @SerialName("creada") val createdAtEpochMs: Long,
    @SerialName("guiado") val guidedJson: String? = null,
) {
    val key: Pair<Long, Long> get() = createdAtEpochMs to startEpochSecond
}

@Serializable
data class PredictionRow(
    @SerialName("calculada") val computedAtEpochSecond: Long,
    @SerialName("hora") val hourKey: Long,
    @SerialName("lugar") val locationKey: String,
    @SerialName("json") val json: String,
) {
    val key: Pair<Long, String> get() = hourKey to locationKey
}

/** Archivo guardado en Drive: la foto y cuándo y desde qué versión se subió. */
@Serializable
data class SyncFile(
    @SerialName("formato") val format: String = FORMAT,
    @SerialName("version") val version: Int = VERSION,
    @SerialName("subido") val uploadedAt: String,
    @SerialName("version_app") val appVersion: String = "",
    @SerialName("datos") val snapshot: SyncSnapshot,
) {
    companion object {
        const val FORMAT = "carpstrategy-sync"
        const val VERSION = 1
    }
}

object SyncJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(snapshot: SyncSnapshot, now: Instant, appVersion: String): String =
        json.encodeToString(SyncFile.serializer(), SyncFile(uploadedAt = now.toString(), appVersion = appVersion, snapshot = snapshot.canonical()))

    /** Null si no es un archivo de CarpStrategy o es de una versión futura que no sabemos leer. */
    fun decode(text: String): SyncSnapshot? {
        val file = runCatching { json.decodeFromString(SyncFile.serializer(), text) }.getOrNull() ?: return null
        if (file.format != SyncFile.FORMAT || file.version > SyncFile.VERSION) return null
        return file.snapshot
    }
}

/**
 * Une dos fotos sin duplicar (para cuando hubo cambios en el móvil y en Drive a la vez): se
 * quedan todas las filas y, si una clave está en las dos, gana la de [preferred].
 */
fun mergeSnapshots(preferred: SyncSnapshot, other: SyncSnapshot): SyncSnapshot {
    fun <T, K> union(a: List<T>, b: List<T>, key: (T) -> K): List<T> {
        val keys = a.map(key).toSet()
        return a + b.filter { key(it) !in keys }
    }
    return SyncSnapshot(
        records = union(preferred.records, other.records) { it.key },
        settings = union(preferred.settings, other.settings) { it.key },
        sessions = union(preferred.sessions, other.sessions) { it.key },
        predictions = union(preferred.predictions, other.predictions) { it.key },
    ).canonical()
}

/** Qué hacer al sincronizar. */
enum class SyncAction {
    /** Nada cambió, o el móvil y Drive ya coinciden. */
    NONE,

    /** Solo cambió el móvil (o Drive está vacío): subir. */
    UPLOAD,

    /** Solo cambió Drive: la app tira de ahí y el móvil queda igual que Drive. */
    DOWNLOAD,

    /** Cambiaron los dos: unir sin duplicar y subir el resultado. */
    MERGE,
}

/**
 * Decide la acción con las huellas: la del móvil ahora, la de la última sincronización y la del
 * archivo de Drive (null si no existe). Función pura.
 */
fun decideSync(localHash: String, localEmpty: Boolean, lastSyncedHash: String?, remoteHash: String?): SyncAction = when {
    remoteHash == null -> if (localEmpty) SyncAction.NONE else SyncAction.UPLOAD
    remoteHash == localHash -> SyncAction.NONE
    // Primera vez en este móvil: si no tiene nada, se baja todo; si tiene datos, se unen.
    lastSyncedHash == null -> if (localEmpty) SyncAction.DOWNLOAD else SyncAction.MERGE
    localHash == lastSyncedHash -> SyncAction.DOWNLOAD
    remoteHash == lastSyncedHash -> SyncAction.UPLOAD
    else -> SyncAction.MERGE
}

fun ManualRecordEntity.toRow() = RecordRow(periodKey, epochSecond, date, latitude, longitude, locationKey, source, origin, originFile, valuesJson, notes, createdAtEpochMs)

fun RecordRow.toEntity() = ManualRecordEntity(
    periodKey = periodKey, epochSecond = epochSecond, date = date, latitude = latitude, longitude = longitude, locationKey = locationKey,
    source = source, origin = origin, originFile = originFile, valuesJson = valuesJson, notes = notes, createdAtEpochMs = createdAtEpochMs,
)

fun SettingEntity.toRow() = SettingRow(key, value)

fun SettingRow.toEntity() = SettingEntity(key, value)

fun SessionEntity.toRow() = SessionRow(
    startEpochSecond, endEpochSecond, latitude, longitude, zone, zoneDetail, depthM, rods, rodHoursOverride, bait, rig, groundbaitKg,
    otherAnglers, bites, losses, catchesJson, blank, predictionJson, contextJson, fulfilled, notes, createdAtEpochMs, guidedJson,
)

fun SessionRow.toEntity(id: Long = 0) = SessionEntity(
    id = id, startEpochSecond = startEpochSecond, endEpochSecond = endEpochSecond, latitude = latitude, longitude = longitude, zone = zone,
    zoneDetail = zoneDetail, depthM = depthM, rods = rods, rodHoursOverride = rodHoursOverride, bait = bait, rig = rig,
    groundbaitKg = groundbaitKg, otherAnglers = otherAnglers, bites = bites, losses = losses, catchesJson = catchesJson, blank = blank,
    predictionJson = predictionJson, contextJson = contextJson, fulfilled = fulfilled, notes = notes, createdAtEpochMs = createdAtEpochMs,
    guidedJson = guidedJson,
)

fun PredictionSnapshotEntity.toRow() = PredictionRow(computedAtEpochSecond, hourKey, locationKey, json)

fun PredictionRow.toEntity() = PredictionSnapshotEntity(computedAtEpochSecond = computedAtEpochSecond, hourKey = hourKey, locationKey = locationKey, json = json)
