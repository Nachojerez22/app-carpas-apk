package com.nachojerez.carpstrategy.data.sync

import java.time.Duration
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/*
 * Borrados que viajan con la sincronización (fase 7.24). Al unir dos fotos se juntaban todas las
 * filas, así que una sesión o un registro borrado en un móvil volvía si el otro (o Drive) aún lo
 * tenía. Cada borrado deja una marca en el ajuste sincronizado [Tombstones.SETTING_KEY]; al unir,
 * se quitan las filas marcadas creadas antes del borrado. Cambiar la hora de inicio de una sesión
 * cambia su clave: la clave vieja también se marca, así no queda duplicada.
 */

/** Marca de borrado: qué fila ([kind] y su clave natural) y cuándo se borró. */
@Serializable
data class Tombstone(
    @SerialName("tipo") val kind: String,
    @SerialName("clave") val key: String,
    @SerialName("borrado") val deletedAtMs: Long,
)

/** Claves de las filas que había en la última sincronización (ajuste local). */
@Serializable
data class SyncedKeys(
    @SerialName("sesiones") val sessions: Set<String> = emptySet(),
    @SerialName("registros") val records: Set<String> = emptySet(),
)

object Tombstones {
    /** Ajuste sincronizado con las marcas (no empieza por `local_`: viaja a Drive). */
    const val SETTING_KEY = "sync_borrados"
    const val SESSION = "sesion"
    const val RECORD = "registro"

    /** Una marca se guarda un año: de sobra para que la vean todos tus móviles. */
    val KEEP: Duration = Duration.ofDays(365)

    private val serializer = ListSerializer(Tombstone.serializer())

    fun key(row: SessionRow): String = "${row.createdAtEpochMs}|${row.startEpochSecond}"

    fun key(row: RecordRow): String = listOf(row.locationKey, row.periodKey, row.source).joinToString("|")

    fun keysOf(snapshot: SyncSnapshot): SyncedKeys =
        SyncedKeys(sessions = snapshot.sessions.map(::key).toSet(), records = snapshot.records.map(::key).toSet())

    fun decode(text: String?): List<Tombstone> =
        text?.let { runCatching { SyncJson.json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    fun encode(list: List<Tombstone>): String = SyncJson.json.encodeToString(serializer, list.sortedWith(compareBy({ it.kind }, { it.key })))

    fun of(snapshot: SyncSnapshot): List<Tombstone> = decode(snapshot.settings.firstOrNull { it.key == SETTING_KEY }?.value)

    /** Junta marcas sin repetir (la del borrado más reciente) y olvida las de hace más de [KEEP]. */
    fun union(a: List<Tombstone>, b: List<Tombstone>, now: Instant): List<Tombstone> {
        val oldest = now.minus(KEEP).toEpochMilli()
        return (a + b).filter { it.deletedAtMs >= oldest }
            .groupBy { it.kind to it.key }
            .map { (_, same) -> same.maxBy { it.deletedAtMs } }
    }

    /**
     * Filas que había en la última sincronización y ya no están: se borraron (o cambiaron de clave)
     * en este móvil. Sin sincronización previa ([last] null) no hay nada que marcar.
     */
    fun deletedSince(last: SyncedKeys?, now: SyncSnapshot, at: Instant): List<Tombstone> {
        if (last == null) return emptyList()
        val current = keysOf(now)
        val ms = at.toEpochMilli()
        return (last.sessions - current.sessions).map { Tombstone(SESSION, it, ms) } +
            (last.records - current.records).map { Tombstone(RECORD, it, ms) }
    }

    /** La foto con las marcas en su ajuste (si no hay ninguna, sin el ajuste). */
    fun withTombstones(snapshot: SyncSnapshot, list: List<Tombstone>): SyncSnapshot {
        val others = snapshot.settings.filter { it.key != SETTING_KEY }
        val settings = if (list.isEmpty()) others else others + SettingRow(SETTING_KEY, encode(list))
        return snapshot.copy(settings = settings).canonical()
    }

    /** Quita las filas marcadas que se crearon antes de su borrado (una fila nueva con la misma clave se queda). */
    fun apply(snapshot: SyncSnapshot, list: List<Tombstone>): SyncSnapshot {
        if (list.isEmpty()) return snapshot
        val sessions = list.filter { it.kind == SESSION }.associate { it.key to it.deletedAtMs }
        val records = list.filter { it.kind == RECORD }.associate { it.key to it.deletedAtMs }
        return snapshot.copy(
            sessions = snapshot.sessions.filter { row -> sessions[key(row)]?.let { row.createdAtEpochMs > it } ?: true },
            records = snapshot.records.filter { row -> records[key(row)]?.let { row.createdAtEpochMs > it } ?: true },
        )
    }

    fun decodeKeys(text: String?): SyncedKeys? = text?.let { runCatching { SyncJson.json.decodeFromString(SyncedKeys.serializer(), it) }.getOrNull() }

    fun encodeKeys(keys: SyncedKeys): String = SyncJson.json.encodeToString(SyncedKeys.serializer(), keys)
}
