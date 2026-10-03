package com.nachojerez.carpstrategy.data.userdata

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Datos del usuario (registros manuales, ajustes y diario). Esquema exportado en app/schemas:
 * cada cambio necesita una migración; NUNCA migración destructiva.
 *
 * Versiones: 1 = registros manuales y ajustes (fase 2); 2 = diario de sesiones y valoraciones
 * previas (fase 6, migración automática: solo añade tablas); 3 = registro de la sesión guiada
 * (fase 7, migración automática: solo añade una columna).
 */
@Database(
    entities = [
        ManualRecordEntity::class,
        SettingEntity::class,
        SessionEntity::class,
        PredictionSnapshotEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class UserDataDatabase : RoomDatabase() {
    abstract fun manualRecordDao(): ManualRecordDao
    abstract fun settingDao(): SettingDao
    abstract fun sessionDao(): SessionDao
    abstract fun predictionSnapshotDao(): PredictionSnapshotDao

    companion object {
        const val NAME = "carpstrategy-usuario.db"
    }
}
