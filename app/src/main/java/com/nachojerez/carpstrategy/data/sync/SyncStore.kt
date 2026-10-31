package com.nachojerez.carpstrategy.data.sync

import androidx.room.withTransaction
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import com.nachojerez.carpstrategy.data.userdata.UserDataDatabase
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Lee y escribe la foto completa de los datos del usuario en la base de datos local. */
@Singleton
class SyncStore @Inject constructor(private val db: UserDataDatabase, private val clock: Clock) {
    private val records get() = db.manualRecordDao()
    private val settings get() = db.settingDao()
    private val sessions get() = db.sessionDao()
    private val predictions get() = db.predictionSnapshotDao()

    suspend fun snapshot(): SyncSnapshot = SyncSnapshot(
        records = records.all().map { it.toRow() },
        settings = settings.all().filterNot { SyncSnapshot.isLocalOnly(it.key) }.map { it.toRow() },
        sessions = sessions.all().map { it.toRow() },
        // Solo las valoraciones recientes (fase 7.24): una sesión copia la suya al crearse, las
        // viejas no sirven en otro móvil y subirlas en cada cambio gasta datos en el embalse.
        predictions = predictions.all().filter { it.computedAtEpochSecond >= clock.instant().minus(PREDICTIONS_WINDOW).epochSecond }.map { it.toRow() },
    ).canonical()

    /**
     * Deja la base de datos igual que [snapshot] (la app «tira» de Drive). Los ajustes locales no
     * se tocan y las sesiones conservan su id local para no romper la que esté en curso.
     */
    suspend fun replaceWith(snapshot: SyncSnapshot) = db.withTransaction {
        records.deleteAll()
        records.insertOrReplace(snapshot.records.map { it.toEntity() })

        val incomingKeys = snapshot.settings.map { it.key }.toSet()
        settings.all().filter { !SyncSnapshot.isLocalOnly(it.key) && it.key !in incomingKeys }.forEach { settings.delete(it.key) }
        snapshot.settings.filterNot { SyncSnapshot.isLocalOnly(it.key) }.forEach { settings.upsert(it.toEntity()) }

        val ids = sessions.all().associate { it.toRow().key to it.id }
        sessions.deleteAll()
        snapshot.sessions.forEach { sessions.upsert(it.toEntity(id = ids[it.key] ?: 0)) }

        // Las valoraciones de este móvil que no viajan se quedan (se limpian solas a los 30 días).
        snapshot.predictions.forEach { predictions.insertOrReplace(it.toEntity()) }
    }

    /** Guarda un ajuste sincronizado (las marcas de borrado, [Tombstones]). */
    suspend fun putSetting(key: String, value: String) = settings.upsert(SettingEntity(key, value))

    /** Emite cuando cambian los datos del usuario (no los ajustes locales ni las valoraciones horarias). */
    fun changes(): Flow<Unit> = combine(
        records.observeAll(),
        sessions.observeAll(),
        settings.observeAll().map { all -> all.filterNot { SyncSnapshot.isLocalOnly(it.key) } }.distinctUntilChanged(),
    ) { r, s, st -> Triple(r, s, st) }.distinctUntilChanged().map { }

    companion object {
        /** Valoraciones que se suben: las de los 2 últimos días (una sesión mira las 24 h previas). */
        val PREDICTIONS_WINDOW: Duration = Duration.ofDays(2)
    }
}
