package com.nachojerez.carpstrategy.data.userdata

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Sesión del diario (fase 6). Las capturas, la valoración previa y el contexto se guardan como
 * JSON (ver [JournalJson]): son datos que se leen siempre junto a la sesión.
 */
@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochSecond: Long,
    /** Null = sesión en curso. */
    val endEpochSecond: Long?,
    val latitude: Double,
    val longitude: Double,
    /** Nombre de [com.nachojerez.carpstrategy.domain.journal.FishingZone] o null. */
    val zone: String?,
    val zoneDetail: String,
    val depthM: Double?,
    val rods: Int,
    val rodHoursOverride: Double?,
    val bait: String,
    val rig: String,
    val groundbaitKg: Double?,
    val otherAnglers: Int?,
    val bites: Int,
    val losses: Int,
    val catchesJson: String,
    /** Respuesta explícita a "¿Bolo?"; null si aún no se ha contestado. */
    val blank: Boolean?,
    val predictionJson: String?,
    val contextJson: String?,
    /** Nombre de [com.nachojerez.carpstrategy.domain.journal.Fulfilled] o null. */
    val fulfilled: String?,
    val notes: String,
    val createdAtEpochMs: Long,
    /** Sesión guiada (fase 7) como JSON; null si se anotó sin guiar. */
    val guidedJson: String? = null,
)

/**
 * Valoración de la app guardada al calcularla (una por hora y lugar) para poder copiarla en
 * las sesiones como "lo que dijo la app antes de salir".
 */
@Entity(
    tableName = "prediction_snapshot",
    indices = [Index(value = ["hourKey", "locationKey"], unique = true)],
)
data class PredictionSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val computedAtEpochSecond: Long,
    /** epochSecond / 3600: una valoración por hora. */
    val hourKey: Long,
    val locationKey: String,
    val json: String,
)

@Dao
interface SessionDao {
    @Query("SELECT * FROM session ORDER BY startEpochSecond DESC, id DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Upsert
    suspend fun upsert(session: SessionEntity): Long

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun delete(id: Long)

    /** Sincronización con Google Drive. */
    @Query("SELECT * FROM session")
    suspend fun all(): List<SessionEntity>

    @Query("DELETE FROM session")
    suspend fun deleteAll()
}

@Dao
interface PredictionSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(snapshot: PredictionSnapshotEntity)

    @Query("SELECT * FROM prediction_snapshot WHERE computedAtEpochSecond BETWEEN :fromEpochSecond AND :toEpochSecond ORDER BY computedAtEpochSecond")
    suspend fun between(fromEpochSecond: Long, toEpochSecond: Long): List<PredictionSnapshotEntity>

    /** Limpieza: las valoraciones viejas que no se copiaron a ninguna sesión no sirven. */
    @Query("DELETE FROM prediction_snapshot WHERE computedAtEpochSecond < :beforeEpochSecond")
    suspend fun deleteOlderThan(beforeEpochSecond: Long)

    /** Sincronización con Google Drive. */
    @Query("SELECT * FROM prediction_snapshot")
    suspend fun all(): List<PredictionSnapshotEntity>

    @Query("DELETE FROM prediction_snapshot")
    suspend fun deleteAll()
}
