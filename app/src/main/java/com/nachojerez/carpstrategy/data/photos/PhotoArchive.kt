package com.nachojerez.carpstrategy.data.photos

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de las fotos de las capturas en un ZIP (fase 7.26): las fotos solo están en el móvil, y
 * así no se pierden al cambiar de móvil o reinstalar. Cada foto va con su nombre en la carpeta
 * `fotos/`; la captura guarda ese nombre, así que al restaurar vuelve a su sitio.
 */
object PhotoArchive {
    private const val FOLDER = "fotos/"

    /** Una foto ya reducida ocupa unos 0,5 MB: lo que pase de esto no es una foto de la app. */
    const val MAX_PHOTO_BYTES = 15L * 1024 * 1024

    private val NAME = Regex("captura_[a-z0-9]{1,32}\\.jpg")

    /** Solo nombres generados por la app: nada de rutas. */
    fun isPhotoName(name: String): Boolean = NAME.matches(name)

    /** Escribe las fotos (nombre → archivo) y devuelve cuántas van. */
    fun write(out: OutputStream, photos: List<Pair<String, File>>): Int {
        var count = 0
        ZipOutputStream(out).use { zip ->
            photos.filter { (name, file) -> isPhotoName(name) && file.isFile }.forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(FOLDER + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        return count
    }

    /**
     * Lee el ZIP y entrega cada foto válida a [store] (nombre y bytes). Ignora lo que no sea una
     * foto de la app (otras carpetas, rutas con «..», nombres ajenos o archivos enormes).
     */
    fun read(input: InputStream, store: (String, ByteArray) -> Unit): Int {
        var count = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix(FOLDER)
                if (!entry.isDirectory && entry.name.startsWith(FOLDER) && isPhotoName(name)) {
                    readLimited(zip)?.let { bytes ->
                        store(name, bytes)
                        count++
                    }
                }
                zip.closeEntry()
            }
        }
        return count
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_PHOTO_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }
}
