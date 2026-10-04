package com.nachojerez.carpstrategy.data.sync

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Error al hablar con Google Drive, clasificado para mostrarlo al usuario. */
sealed class DriveException(message: String) : Exception(message) {
    /** El permiso ha caducado o se retiró: hay que volver a iniciar sesión. */
    class Unauthorized : DriveException("Sin permiso para Google Drive")

    /** Sin conexión o el servidor no responde. */
    class Offline(cause: IOException) : DriveException(cause.message ?: "Sin conexión")

    class Failed(val code: Int) : DriveException("Google Drive respondió $code")
}

/** El archivo de sincronización en Drive: id, fecha y huella del contenido. */
data class RemoteFile(val id: String, val modifiedTime: String?, val hash: String?)

/**
 * Acceso a la carpeta privada de la app en Google Drive (`appDataFolder`, permiso
 * `drive.appdata`): solo ve sus propios archivos, no los del usuario. API REST v3 con OkHttp.
 */
class DriveClient(
    private val client: OkHttpClient,
    private val apiBase: HttpUrl = API_BASE.toHttpUrl(),
    private val uploadBase: HttpUrl = UPLOAD_BASE.toHttpUrl(),
) {
    @Serializable
    private data class FileDto(
        val id: String,
        val modifiedTime: String? = null,
        val appProperties: Map<String, String> = emptyMap(),
    )

    @Serializable
    private data class FileListDto(val files: List<FileDto> = emptyList())

    @Serializable
    private data class UserDto(@SerialName("emailAddress") val email: String? = null)

    @Serializable
    private data class AboutDto(val user: UserDto? = null)

    private val json = Json { ignoreUnknownKeys = true }

    /** Busca el archivo de sincronización; null si aún no existe. */
    fun find(token: String): RemoteFile? {
        val url = apiBase.newBuilder()
            .addPathSegments("files")
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id,modifiedTime,appProperties)")
            .build()
        val body = execute(Request.Builder().url(url).get(), token)
        return json.decodeFromString(FileListDto.serializer(), body).files.firstOrNull()?.toRemote()
    }

    fun download(token: String, id: String): String {
        val url = apiBase.newBuilder().addPathSegments("files").addPathSegment(id).addQueryParameter("alt", "media").build()
        return execute(Request.Builder().url(url).get(), token)
    }

    /** Crea el archivo o lo sustituye si [existingId] no es null; guarda la huella en sus propiedades. */
    fun upload(token: String, existingId: String?, content: String, hash: String): RemoteFile {
        val metadata = buildString {
            append("{\"appProperties\":{\"$HASH_PROPERTY\":\"").append(hash).append("\"}")
            if (existingId == null) append(",\"name\":\"$FILE_NAME\",\"parents\":[\"appDataFolder\"]")
            append('}')
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(JSON))
            .addPart(content.toRequestBody(JSON))
            .build()
        val url = uploadBase.newBuilder().addPathSegments("files").apply { if (existingId != null) addPathSegment(existingId) }
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,modifiedTime,appProperties")
            .build()
        val request = Request.Builder().url(url).apply { if (existingId == null) post(body) else patch(body) }
        return json.decodeFromString(FileDto.serializer(), execute(request, token)).toRemote()
    }

    /** Correo de la cuenta, para mostrar con qué cuenta se sincroniza. */
    fun accountEmail(token: String): String? {
        val url = apiBase.newBuilder().addPathSegments("about").addQueryParameter("fields", "user(emailAddress)").build()
        return json.decodeFromString(AboutDto.serializer(), execute(Request.Builder().url(url).get(), token)).user?.email
    }

    private fun FileDto.toRemote() = RemoteFile(id, modifiedTime, appProperties[HASH_PROPERTY])

    private fun execute(builder: Request.Builder, token: String): String {
        val request = builder.header("Authorization", "Bearer $token").build()
        val response: Response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw DriveException.Offline(e)
        }
        response.use {
            if (it.code == 401) throw DriveException.Unauthorized()
            if (!it.isSuccessful) throw DriveException.Failed(it.code)
            return it.body.string()
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "carpstrategy-sync.json"
        const val HASH_PROPERTY = "huella"
        private const val API_BASE = "https://www.googleapis.com/drive/v3/"
        private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3/"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
