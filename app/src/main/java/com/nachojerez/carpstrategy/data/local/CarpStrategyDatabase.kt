package com.nachojerez.carpstrategy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Caché meteorológica. Todo su contenido se puede volver a descargar, así que de momento usa
 * migración destructiva y no exporta el esquema. El diario de sesiones (fase 5) NO puede
 * perderse: cuando llegue, irá con esquema exportado y migraciones reales.
 */
@Database(
    entities = [
        ForecastMetaEntity::class,
        ForecastHourEntity::class,
        StationEntity::class,
        ObservationEntity::class,
        ObservationMetaEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CarpStrategyDatabase : RoomDatabase() {
    abstract fun forecastDao(): ForecastDao
    abstract fun observationDao(): ObservationDao
    abstract fun stationDao(): StationDao

    companion object {
        const val NAME = "carpstrategy.db"
    }
}
