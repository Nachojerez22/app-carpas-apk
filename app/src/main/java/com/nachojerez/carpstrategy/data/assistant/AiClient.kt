package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.domain.assistant.AiCall
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.guided.AiCheckInMode
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
    /** Cuándo consultar en los avisos de la sesión guiada (fase 7.15: por defecto, si cambia algo). */
    val checkInMode: AiCheckInMode = AiCheckInMode.ON_CHANGE,
) {
    companion object {
        /** Modelo por defecto; se puede cambiar por el que muestre Google AI Studio. */
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/"
    }
}

/** Error al consultar la IA, clasificado para mostrarlo. */
sealed class AiException(message: String) : Exception(message) {
    /** Lo que dijo el proveedor o la red (código y mensaje, sin la clave), para enseñarlo. */
    val description: String?
        get() = when (this) {
            is Failed -> listOfNotNull("HTTP $code", detail).joinToString(": ")
            is Quota -> detail
            is Offline -> detail
            else -> null
        }

    /** Clave no válida o sin permiso. */
    class Unauthorized : AiException("Clave no válida o sin permiso")

    /** Cuota agotada o demasiadas peticiones (nivel gratuito). */
    /** [detail]: el mensaje de Google (sin la clave), p. ej. qué cuota se ha agotado. */
    class Quota(val detail: String? = null) : AiException("Cuota agotada o demasiadas consultas")

    /**
     * No se pudo hablar con el proveedor. [reason] dice por qué (para no quedarse en «Sin conexión»)
     * y [detail] es el error técnico, sin la clave.
     */
    class Offline(cause: IOException, val detail: String? = null) : AiException(cause.message ?: "Sin conexión") {
        val reason: OfflineReason = when (cause) {
            is UnknownHostException -> OfflineReason.NO_DNS
            is InterruptedIOException -> OfflineReason.TIMEOUT
            is SSLException -> OfflineReason.TLS
            is ConnectException, is NoRouteToHostException -> OfflineReason.REFUSED
            else -> OfflineReason.CUT
        }
    }

    /** [detail]: el mensaje del proveedor (sin la clave), para saber qué ha pasado. */
    class Failed(val code: Int, val detail: String? = null) : AiException("El proveedor respondió $code")

    /** Respuesta vacía o bloqueada por el proveedor. */
    class Empty : AiException("Respuesta vacía")

    class NotConfigured : AiException("Falta la clave o el modelo")
}

/**
 * Por qué falló la red: el móvil no encuentra el servidor (sin datos, DNS privado o bloqueador),
 * tardó demasiado, falló la conexión segura (fecha y hora del móvil), no se pudo conectar o se cortó.
 */
enum class OfflineReason { NO_DNS, TIMEOUT, TLS, REFUSED, CUT }

/** Quien guarda el consumo de cada consulta (fase 7.14); por defecto, nadie. */
fun interface AiUsageRecorder {
    fun record(call: AiCall)
}

/**
 * Cliente HTTP mínimo (OkHttp) para pedir una respuesta JSON a un modelo de lenguaje. Sin
 * registro de peticiones: la clave nunca sale a logcat. Bloqueante: llamar desde IO. Cada
 * respuesta (tokens y búsquedas) y cada «cuota agotada» se apuntan en [recorder].
 */
class AiClient(
    client: OkHttpClient,
    private val geminiBase: String = AiConfig.GEMINI_BASE,
    private val recorder: AiUsageRecorder = AiUsageRecorder {},
    private val clock: Clock = Clock.systemUTC(),
) {
    private val http = client.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(75, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()

    /**
     * [web]: con Gemini, deja que el modelo busque en Google y lea los enlaces del mensaje
     * (herramientas `google_search` y `url_context`, fase 7.13). Con herramientas no se puede pedir
     * JSON estricto, así que el JSON se busca dentro del texto. Otros proveedores lo ignoran.
     * [search] = false: solo lee los enlaces (`url_context`), sin buscar en Google, que tiene su
     * propia cuota (fase 7.15).
     */
    fun complete(config: AiConfig, apiKey: String, system: String, user: String, web: Boolean = false, kind: AiKind? = null, search: Boolean = true): String {
        if (apiKey.isBlank() || config.model.isBlank()) throw AiException.NotConfigured()
        try {
            return when (config.provider) {
                AiProvider.GEMINI -> gemini(config, apiKey, system, user, web, kind, search)
                AiProvider.OPENAI_COMPATIBLE -> openAi(config, apiKey, system, user, kind)
            }
        } catch (e: AiException.Quota) {
            note(AiCall(clock.instant(), kind, quota = true, quotaDetail = e.detail))
            throw e
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

    private fun gemini(config: AiConfig, apiKey: String, system: String, user: String, web: Boolean = false, kind: AiKind? = null, search: Boolean = true): String {
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
            if (web) {
                putJsonArray("tools") {
                    if (search) addJsonObject { putJsonObject("google_search") {} }
                    addJsonObject { putJsonObject("url_context") {} }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", TEMPERATURE)
                if (!web) put("responseMimeType", "application/json")
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
        val candidate = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        // Consumo (fase 7.14): tokens de usageMetadata y búsquedas de groundingMetadata.
        val usage = response["usageMetadata"] as? JsonObject
        note(
            AiCall(
                time = clock.instant(),
                kind = kind,
                inputTokens = usage.long("promptTokenCount") + usage.long("toolUsePromptTokenCount"),
                outputTokens = usage.long("candidatesTokenCount"),
                thinkingTokens = usage.long("thoughtsTokenCount"),
                searches = (candidate?.get("groundingMetadata") as? JsonObject)?.get("webSearchQueries")?.let { it as? JsonArray }?.size ?: 0,
            ),
        )
        // Los modelos con razonamiento pueden devolver partes «thought»: se ignoran.
        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray.orEmpty()
        val text = parts.mapNotNull { p ->
            val obj = p.jsonObject
            if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) null else obj["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")
        return text.ifBlank { throw AiException.Empty() }
    }

    private fun openAi(config: AiConfig, apiKey: String, system: String, user: String, kind: AiKind? = null): String {
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
        val usage = response["usage"] as? JsonObject
        val reasoning = (usage?.get("completion_tokens_details") as? JsonObject).long("reasoning_tokens")
        note(
            AiCall(
                time = clock.instant(),
                kind = kind,
                inputTokens = usage.long("prompt_tokens"),
                outputTokens = usage.long("completion_tokens") - reasoning,
                thinkingTokens = reasoning,
            ),
        )
        val text = response["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
        return text?.ifBlank { null } ?: throw AiException.Empty()
    }

    /** Apuntar el consumo nunca debe estropear la consulta. */
    private fun note(call: AiCall) {
        runCatching { recorder.record(call) }
    }

    /**
     * Con cobertura justa la conexión se corta a veces a mitad, y Google responde a menudo 503
     * («modelo saturado») o 500 durante unos segundos: se repite una vez. No se repite si el móvil no
     * encuentra el servidor (no va a cambiar en un segundo) ni si ya se agotó el tiempo.
     */
    private fun execute(request: Request, secret: String): JsonObject {
        var attempt = 1
        while (true) {
            try {
                return executeOnce(request, secret)
            } catch (e: AiException) {
                val transient = when (e) {
                    is AiException.Offline -> e.reason != OfflineReason.NO_DNS && e.reason != OfflineReason.TIMEOUT
                    is AiException.Failed -> e.code in SERVER_ERRORS
                    else -> false
                }
                if (!transient || attempt >= NETWORK_ATTEMPTS) throw e
                attempt++
                Thread.sleep(if (e is AiException.Failed) SERVER_RETRY_DELAY_MS else RETRY_DELAY_MS)
            }
        }
    }

    private fun executeOnce(request: Request, secret: String): JsonObject {
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.code == 401 || response.code == 403 -> throw AiException.Unauthorized()
                    // Gemini responde 400 «API key not valid» con una clave errónea.
                    response.code == 400 && text.contains("API_KEY_INVALID") -> throw AiException.Unauthorized()
                    response.code == 429 -> throw AiException.Quota(errorDetail(text, secret))
                    !response.isSuccessful -> throw AiException.Failed(response.code, errorDetail(text, secret))
                }
                return runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw AiException.Empty() }
            }
        } catch (e: IOException) {
            val detail = listOfNotNull(e::class.java.simpleName, e.message).joinToString(": ")
            throw AiException.Offline(e, detail.replace(secret, "…").take(MAX_DETAIL))
        }
    }

    /** `error.message` de la respuesta (Google y OpenAI), recortado y sin la clave. */
    private fun errorDetail(text: String, secret: String): String? {
        val error = runCatching { json.parseToJsonElement(text).jsonObject["error"] }.getOrNull() ?: return null
        val message = (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            ?: (error as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        return message?.replace(secret, "…")?.take(MAX_DETAIL)
    }

    /** Número entero de un campo del JSON de consumo (0 si no viene). */
    private fun JsonObject?.long(key: String): Long = (this?.get(key) as? JsonPrimitive)?.longOrNull ?: 0L

    private companion object {
        const val MAX_DETAIL = 300

        /** Intentos ante un corte de red (el primero más un reintento). */
        const val NETWORK_ATTEMPTS = 2
        const val RETRY_DELAY_MS = 1_000L

        /** Errores pasajeros del servidor del proveedor (saturado, puerta de enlace…). */
        val SERVER_ERRORS = setOf(500, 502, 503, 504)
        const val SERVER_RETRY_DELAY_MS = 2_000L

        /** Respuestas estables: poca creatividad. */
        const val TEMPERATURE = 0.2
    }
}
