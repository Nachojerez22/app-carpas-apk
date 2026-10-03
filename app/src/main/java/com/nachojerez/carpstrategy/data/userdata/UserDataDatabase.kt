package com.nachojerez.carpstrategy.data.userdata

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Datos del usuario (registros manuales, ajustes y, en fase 5, el diario). Esquema exportado en
 * app/schemas: cada cambio necesita una migración; NUNCA migración destructiva.
 */
@Database(
    entities = [ManualRecordEntity::class, SettingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun manualRecordDao(): ManualRecordDao
    abstract fun settingDao(): SettingDao

    companion object {
        const val NAME = "carpstrategy-usuario.db"
    }
}
