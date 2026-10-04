package com.nachojerez.carpstrategy.data.update

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Versión publicada en GitHub (Releases), con el APK listo para descargar. */
data class AvailableUpdate(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val notes: String,
)

/**
 * Comprueba si hay una versión nueva en las Releases del repositorio (público). Solo pregunta
 * cuál es la última: no envía ningún dato del usuario. La CI publica cada versión de `main` con
 * la etiqueta `v<versionName>-<versionCode>` y el APK `CarpStrategy.apk`.
 */
class UpdateChecker(
    private val client: OkHttpClient,
    private val latestUrl: HttpUrl = LATEST_URL.toHttpUrl(),
) {
    @Serializable
    private data class AssetDto(val name: String, @SerialName("browser_download_url") val url: String)

    @Serializable
    private data class ReleaseDto(
        @SerialName("tag_name") val tag: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetDto> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** La versión publicada si es más nueva que [currentVersionCode]; null si no, o si falla la red. */
    fun check(currentVersionCode: Int): AvailableUpdate? {
        val text = try {
            client.newCall(Request.Builder().url(latestUrl).header("Accept", "application/vnd.github+json").build()).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.string()
            }
        } catch (_: IOException) {
            return null
        }
        return parse(text)?.takeIf { it.versionCode > currentVersionCode }
    }

    /** Lee la respuesta de GitHub; null si no es una versión válida de la app. */
    fun parse(text: String): AvailableUpdate? {
        val release = runCatching { json.decodeFromString(ReleaseDto.serializer(), text) }.getOrNull() ?: return null
        if (release.draft || release.prerelease) return null
        val (name, code) = parseTag(release.tag) ?: return null
        val apk = release.assets.firstOrNull { it.name == APK_NAME } ?: release.assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return AvailableUpdate(name, code, apk.url, release.body.orEmpty().trim())
    }

    companion object {
        const val APK_NAME = "CarpStrategy.apk"
        private const val LATEST_URL = "https://api.github.com/repos/Nachojerez22/app-carpas-apk/releases/latest"
        private val TAG = Regex("""^v(.+)-(\d+)$""")

        /** `v0.8.2-52` → ("0.8.2", 52). */
        fun parseTag(tag: String): Pair<String, Int>? =
            TAG.matchEntire(tag.trim())?.let { m -> m.groupValues[2].toIntOrNull()?.let { m.groupValues[1] to it } }
    }
}
