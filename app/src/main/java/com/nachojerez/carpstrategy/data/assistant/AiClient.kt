package com.nachojerez.carpstrategy.data.assistant

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Protocolo del proveedor. Gemini (Google AI Studio) o cualquiera compatible con OpenAI. */
enum class AiProvider { GEMINI, OPENAI_COMPATIBLE }

/**
 * Configuración del asistente (ajuste local `ai_config`, nunca se sincroniza). La clave va
 * aparte (`ai_key`) y nunca se guarda en sesiones, exportaciones ni registros.
 */
data class AiConfig(
    val provider: AiProvider = AiProvider.GEMINI,
    val model: String = DEFAULT_GEMINI_MODEL,
    /** Solo para OPENAI_COMPATIBLE (p. ej. `https://api.openai.com/v1`). */
    val baseUrl: String = "",
    /** Consultar automáticamente en cada aviso de la sesión guiada. */
    val autoCheckIn: Boolean = true,
) {
    companion object {
        /** Modelo por defecto; se puede cambiar por el que muestre Google AI Studio. */
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/"
    }
}

/** Error al consultar la IA, clasificado para mostrarlo. */
sealed class AiException(message: String) : Exception(message) {
    /** Clave no válida o sin permiso. */
    class Unauthorized : AiException("Clave no válida o sin permiso")

    /** Cuota agotada o demasiadas peticiones (nivel gratuito). */
    class Quota : AiException("Cuota agotada o demasiadas consultas")

    class Offline(cause: IOException) : AiException(cause.message ?: "Sin conexión")

    /** [detail]: el mensaje del proveedor (sin la clave), para saber qué ha pasado. */
    class Failed(val code: Int, val detail: String? = null) : AiException("El proveedor respondió $code")

    /** Respuesta vacía o bloqueada por el proveedor. */
    class Empty : AiException("Respuesta vacía")

    class NotConfigured : AiException("Falta la clave o el modelo")
}

/**
 * Cliente HTTP mínimo (OkHttp) para pedir una respuesta JSON a un modelo de lenguaje. Sin
 * registro de peticiones: la clave nunca sale a logcat. Bloqueante: llamar desde IO.
 */
class AiClient(client: OkHttpClient, private val geminiBase: String = AiConfig.GEMINI_BASE) {
    private val http = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    fun complete(config: AiConfig, apiKey: String, system: String, user: String): String {
        if (apiKey.isBlank() || config.model.isBlank()) throw AiException.NotConfigured()
        return when (config.provider) {
            AiProvider.GEMINI -> gemini(config, apiKey, system, user)
            AiProvider.OPENAI_COMPATIBLE -> openAi(config, apiKey, system, user)
        }
    }

    /**
     * Modelos que admite tu clave y sirven para generar texto (Gemini: `generateContent`).
     * Así el usuario elige uno que existe en lugar de adivinar el nombre.
     */
    fun listModels(config: AiConfig, apiKey: String): List<String> {
        if (apiKey.isBlank()) throw AiException.NotConfigured()
        return when (config.provider) {
            AiProvider.GEMINI -> {
                val url = geminiBase.toHttpUrlOrNull()?.newBuilder()?.addPathSegment("models")
                    ?.addQueryParameter("pageSize", "200")?.build() ?: throw AiException.NotConfigured()
                val response = execute(Request.Builder().url(url).header("x-goog-api-key", apiKey.trim()).get().build(), apiKey.trim())
                response["models"]?.jsonArray.orEmpty().mapNotNull { e ->
                    val m = e.jsonObject
                    val methods = m["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                    m["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")?.takeIf { "generateContent" in methods }
                }
            }
            AiProvider.OPENAI_COMPATIBLE -> {
                val url = "${config.baseUrl.trim().trimEnd('/')}/models".toHttpUrlOrNull() ?: throw AiException.NotConfigured()
                val response = execute(Request.Builder().url(url).header("Authorization", "Bearer ${apiKey.trim()}").get().build(), apiKey.trim())
                response["data"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull }
            }
        }.distinct().sortedWith(compareBy({ !it.contains("flash") }, { it }))
    }

    private fun gemini(config: AiConfig, apiKey: String, system: String, user: String): String {
        val key = apiKey.trim()
        fun url(base: String) = base.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegment("models")
            ?.addPathSegment("${config.model.trim()}:generateContent")
            ?.build() ?: throw AiException.NotConfigured()
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", TEMPERATURE)
                put("responseMimeType", "application/json")
            }
        }
        val payload = body.toString()
        // Con algunas claves Google responde 404 a una de las formas de llamar: se prueban, por
        // orden, clave en cabecera, clave en la URL y la versión estable de la API.
        val v1 = geminiBase.replace("/v1beta/", "/v1/").takeIf { it != geminiBase }
        val attempts = listOfNotNull<() -> Request>(
            { Request.Builder().url(url(geminiBase)).header("x-goog-api-key", key).post(payload.toRequestBody(mediaJson)).build() },
            { Request.Builder().url(url(geminiBase).newBuilder().addQueryParameter("key", key).build()).post(payload.toRequestBody(mediaJson)).build() },
            v1?.let { base -> { Request.Builder().url(url(base)).header("x-goog-api-key", key).post(payload.toRequestBody(mediaJson)).build() } },
        )
        var notFound: AiException.Failed? = null
        var response: JsonObject? = null
        for (attempt in attempts) {
            try {
                response = execute(attempt(), key)
                break
            } catch (e: AiException.Failed) {
                if (e.code != 404) throw e
                if (notFound == null) notFound = e
            }
        }
        if (response == null) throw notFound ?: AiException.Empty()
        // Los modelos con razonamiento pueden devolver partes «thought»: se ignoran.
        val parts = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts.mapNotNull { p ->
            val obj = p.jsonObject
            if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) null else obj["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")
        return text.ifBlank { throw AiException.Empty() }
    }

    private fun openAi(config: AiConfig, apiKey: String, system: String, user: String): String {
        val base = config.baseUrl.trim().trimEnd('/')
        val url = "$base/chat/completions".toHttpUrlOrNull() ?: throw AiException.NotConfigured()
        val body = buildJsonObject {
            put("model", config.model.trim())
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", user)
                }
            }
            put("temperature", TEMPERATURE)
            putJsonObject("response_format") { put("type", "json_object") }
        }
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toString().toRequestBody(mediaJson))
            .build()
        val response = execute(request, apiKey.trim())
        val text = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return text?.ifBlank { null } ?: throw AiException.Empty()
    }

    private fun execute(request: Request, secret: String): JsonObject {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.code == 401 || response.code == 403 -> throw AiException.Unauthorized()
                    // Gemini responde 400 «API key not valid» con una clave errónea.
                    response.code == 400 && text.contains("API_KEY_INVALID") -> throw AiException.Unauthorized()
                    response.code == 429 -> throw AiException.Quota()
                    !response.isSuccessful -> throw AiException.Failed(response.code, errorDetail(text, secret))
                }
                return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw AiException.Empty() }
            }
        } catch (e: IOException) {
            throw AiException.Offline(e)
        }
    }

    /** `error.message` de la respuesta (Google y OpenAI), recortado y sin la clave. */
    private fun errorDetail(text: String, secret: String): String? {
        val error = runCatching { json.parseToJsonElement(text).jsonObject["error"] }.getOrNull() ?: return null
        val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?: (error as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return message?.replace(secret, "…")?.take(MAX_DETAIL)
    }

    private companion object {
        const val MAX_DETAIL = 300

        /** Respuestas estables: poca creatividad. */
        const val TEMPERATURE = 0.2
    }
}
