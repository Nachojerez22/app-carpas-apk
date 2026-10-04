package com.nachojerez.carpstrategy.di

import com.nachojerez.carpstrategy.BuildConfig
import com.nachojerez.carpstrategy.data.remote.aemet.AemetApi
import com.nachojerez.carpstrategy.data.remote.aemet.AemetApiKeyInterceptor
import com.nachojerez.carpstrategy.data.remote.aemet.AemetDataSource
import com.nachojerez.carpstrategy.data.remote.openmeteo.OpenMeteoApi
import com.nachojerez.carpstrategy.data.sync.DriveClient
import com.nachojerez.carpstrategy.data.update.UpdateChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Sin interceptor de logging: evitaría filtrar la API key de AEMET a logcat. */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, json: Json): OpenMeteoApi = Retrofit.Builder()
        .baseUrl(OpenMeteoApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    @Provides
    @Named(AemetDataSource.AEMET_API_KEY)
    fun provideAemetApiKey(): String = BuildConfig.AEMET_API_KEY

    @Provides
    @Singleton
    fun provideAemetApi(
        client: OkHttpClient,
        @Named(AemetDataSource.AEMET_API_KEY) apiKey: String,
    ): AemetApi = Retrofit.Builder()
        .baseUrl(AemetApi.BASE_URL)
        .client(client.newBuilder().addInterceptor(AemetApiKeyInterceptor(apiKey)).build())
        .build()
        .create()

    @Provides
    @Singleton
    fun provideDriveClient(client: OkHttpClient): DriveClient = DriveClient(client)

    @Provides
    @Singleton
    fun provideUpdateChecker(client: OkHttpClient): UpdateChecker = UpdateChecker(client)
}
