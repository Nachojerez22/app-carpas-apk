package com.nachojerez.carpstrategy.domain.repository

import com.nachojerez.carpstrategy.domain.manual.ManualRecord
import com.nachojerez.carpstrategy.domain.manual.SourcePriority
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings
import com.nachojerez.carpstrategy.domain.model.FishingLocation
import com.nachojerez.carpstrategy.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

/** Datos introducidos o importados por el usuario. Nunca se borran sin que él lo pida. */
interface ManualDataRepository {
    /** Registros a menos de [radiusKm] de [location], del más reciente al más antiguo. */
    fun observeRecords(location: GeoPoint, radiusKm: Double = DEFAULT_RADIUS_KM): Flow<List<ManualRecord>>

    /** Inserta o, si `id != 0`, sustituye. Devuelve el id. */
    suspend fun save(record: ManualRecord): Long

    suspend fun delete(id: Long)

    /** Importa en bloque. Un registro con la misma hora/fecha y fuente sustituye al anterior. */
    suspend fun import(records: List<ManualRecord>): Int

    companion object {
        const val DEFAULT_RADIUS_KM = 10.0
    }
}

interface SettingsRepository {
    fun observeSourcePriority(): Flow<SourcePriority>
    suspend fun setSourcePriority(priority: SourcePriority)

    fun observeAppearance(): Flow<AppearanceSettings>
    suspend fun setAppearance(settings: AppearanceSettings)

    /** Ubicación de trabajo; por defecto el embalse de Brovales. */
    fun observeLocation(): Flow<FishingLocation>
    suspend fun setLocation(location: FishingLocation)
}
