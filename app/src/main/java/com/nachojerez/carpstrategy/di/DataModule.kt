package com.nachojerez.carpstrategy.di

import android.content.Context
import androidx.room.Room
import com.nachojerez.carpstrategy.data.local.CarpStrategyDatabase
import com.nachojerez.carpstrategy.data.local.ForecastDao
import com.nachojerez.carpstrategy.data.local.ObservationDao
import com.nachojerez.carpstrategy.data.local.StationDao
import com.nachojerez.carpstrategy.data.repository.WeatherRepositoryImpl
import com.nachojerez.carpstrategy.data.rules.AssetRulesRepository
import com.nachojerez.carpstrategy.data.userdata.JournalRepositoryImpl
import com.nachojerez.carpstrategy.data.userdata.ManualDataRepositoryImpl
import com.nachojerez.carpstrategy.data.userdata.ManualRecordDao
import com.nachojerez.carpstrategy.data.userdata.PredictionSnapshotDao
import com.nachojerez.carpstrategy.data.userdata.SessionDao
import com.nachojerez.carpstrategy.data.userdata.SettingDao
import com.nachojerez.carpstrategy.data.userdata.SettingsRepositoryImpl
import com.nachojerez.carpstrategy.data.userdata.UserDataDatabase
import com.nachojerez.carpstrategy.domain.repository.JournalRepository
import com.nachojerez.carpstrategy.domain.repository.ManualDataRepository
import com.nachojerez.carpstrategy.domain.repository.SettingsRepository
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CarpStrategyDatabase =
        Room.databaseBuilder(context, CarpStrategyDatabase::class.java, CarpStrategyDatabase.NAME)
            // Solo caché re-descargable (ver CarpStrategyDatabase).
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideForecastDao(db: CarpStrategyDatabase): ForecastDao = db.forecastDao()

    @Provides
    fun provideObservationDao(db: CarpStrategyDatabase): ObservationDao = db.observationDao()

    @Provides
    fun provideStationDao(db: CarpStrategyDatabase): StationDao = db.stationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindWeatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UserDataModule {
    /** Sin migración destructiva: son datos del usuario (ver UserDataDatabase). */
    @Provides
    @Singleton
    fun provideUserDataDatabase(@ApplicationContext context: Context): UserDataDatabase =
        Room.databaseBuilder(context, UserDataDatabase::class.java, UserDataDatabase.NAME).build()

    @Provides
    fun provideManualRecordDao(db: UserDataDatabase): ManualRecordDao = db.manualRecordDao()

    @Provides
    fun provideSettingDao(db: UserDataDatabase): SettingDao = db.settingDao()

    @Provides
    fun provideSessionDao(db: UserDataDatabase): SessionDao = db.sessionDao()

    @Provides
    fun providePredictionSnapshotDao(db: UserDataDatabase): PredictionSnapshotDao = db.predictionSnapshotDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UserDataRepositoryModule {
    @Binds
    abstract fun bindManualDataRepository(impl: ManualDataRepositoryImpl): ManualDataRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RulesModule {
    @Binds
    abstract fun bindRulesRepository(impl: AssetRulesRepository): RulesRepository
}
