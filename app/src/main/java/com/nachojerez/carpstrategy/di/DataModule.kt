package com.nachojerez.carpstrategy.di

import android.content.Context
import androidx.room.Room
import com.nachojerez.carpstrategy.data.local.CarpStrategyDatabase
import com.nachojerez.carpstrategy.data.local.ForecastDao
import com.nachojerez.carpstrategy.data.local.ObservationDao
import com.nachojerez.carpstrategy.data.local.StationDao
import com.nachojerez.carpstrategy.data.repository.WeatherRepositoryImpl
import com.nachojerez.carpstrategy.domain.repository.WeatherRepository
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
