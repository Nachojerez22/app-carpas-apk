package com.nachojerez.carpstrategy.data.manual

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

/** Escribe un archivo elegido por el usuario (selector "Guardar como" del sistema). */
class DocumentWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** @throws IOException si no se puede escribir. */
    fun write(uri: Uri, text: String) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("No se pudo abrir el archivo")
        output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
