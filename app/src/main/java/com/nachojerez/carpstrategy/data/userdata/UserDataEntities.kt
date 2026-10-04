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
 * Registro manual o importado. [periodKey] identifica la hora ("t:<epochSecond>") o el día
 * ("d:<yyyy-MM-dd>") para evitar duplicados al reimportar el mismo archivo.
 */
@Entity(
    tableName = "manual_record",
    indices = [Index(value = ["locationKey", "periodKey", "source"], unique = true)],
)
data class ManualRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodKey: String,
    /** Instante (UTC) para registros por hora; null en registros por día. */
    val epochSecond: Long?,
    /** Día (ISO yyyy-MM-dd) para registros por día; null en registros por hora. */
    val date: String?,
    val latitude: Double,
    val longitude: Double,
    val locationKey: String,
    val source: String,
    /** "typed" o "imported". */
    val origin: String,
    val originFile: String?,
    /** Valores como objeto JSON {"temp_aire_c": 14.2, …} con las claves de docs/FORMATO_DATOS.md. */
    val valuesJson: String,
    val notes: String?,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface ManualRecordDao {
    @Query("SELECT * FROM manual_record ORDER BY COALESCE(epochSecond, 0) DESC, date DESC, id DESC")
    fun observeAll(): Flow<List<ManualRecordEntity>>

    @Upsert
    suspend fun upsert(record: ManualRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(records: List<ManualRecordEntity>): List<Long>

    @Query("DELETE FROM manual_record WHERE id = :id")
    suspend fun delete(id: Long)

    /** Sincronización con Google Drive. */
    @Query("SELECT * FROM manual_record")
    suspend fun all(): List<ManualRecordEntity>

    @Query("DELETE FROM manual_record")
    suspend fun deleteAll()
}

@Dao
interface SettingDao {
    @Query("SELECT value FROM setting WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun upsert(setting: SettingEntity)

    /** Sincronización con Google Drive. */
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>

    @Query("DELETE FROM setting WHERE `key` = :key")
    suspend fun delete(key: String)
}
