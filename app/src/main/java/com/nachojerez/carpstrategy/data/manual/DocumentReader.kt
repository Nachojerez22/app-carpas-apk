package com.nachojerez.carpstrategy.data.manual

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

/** Lee un archivo elegido por el usuario (selector de documentos del sistema). */
class DocumentReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    data class Document(val name: String, val text: String)

    /** @throws IOException si no se puede leer o supera [MAX_BYTES]. */
    fun read(uri: Uri): Document {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: "archivo.json"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(MAX_BYTES + 1)
            if (buffer.size > MAX_BYTES) throw IOException("Archivo demasiado grande (máx. ${MAX_BYTES / 1024} KB)")
            buffer
        } ?: throw IOException("No se pudo abrir el archivo")
        return Document(name, bytes.toString(Charsets.UTF_8).removePrefix("﻿"))
    }

    /** Lee el JSON de ejemplo incluido en la app. */
    fun readExample(): String = context.assets.open(EXAMPLE_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        const val EXAMPLE_ASSET = "datos-ejemplo.json"
    }
}
