package com.nachojerez.carpstrategy.data.remote.aemet

import okhttp3.Interceptor
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * AEMET OpenData (https://opendata.aemet.es/). Cada consulta va en dos pasos: la primera
 * respuesta es un sobre JSON con una URL temporal en `datos`, y esa URL devuelve los datos.
 * Las respuestas se piden como [ResponseBody] porque AEMET sirve texto en ISO-8859-15.
 */
interface AemetApi {
    @GET("opendata/api/valores/climatologicos/inventarioestaciones/todasestaciones")
    suspend fun stationInventory(): Response<ResponseBody>

    /** Observación convencional de las últimas ~24 h de una estación. */
    @GET("opendata/api/observacion/convencional/datos/estacion/{idema}")
    suspend fun stationObservations(@Path("idema") stationId: String): Response<ResponseBody>

    /** Segundo paso: descarga la URL de `datos` del sobre. */
    @GET
    suspend fun download(@Url url: String): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://opendata.aemet.es/"
        const val API_KEY_HEADER = "api_key"
        const val HOST = "opendata.aemet.es"
    }
}

/** Añade la API key de AEMET (de BuildConfig) solo a las peticiones a AEMET. */
class AemetApiKeyInterceptor(private val apiKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (apiKey.isBlank() || request.url.host != AemetApi.HOST) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AemetApi.API_KEY_HEADER, apiKey).build())
    }
}
