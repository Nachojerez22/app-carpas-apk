package com.nachojerez.carpstrategy.data.photos

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PhotoArchiveTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `ida y vuelta de las fotos con su nombre`() {
        val a = File(dir.toFile(), "captura_abc123.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val missing = File(dir.toFile(), "captura_nohay.jpg")
        val out = ByteArrayOutputStream()
        val written = PhotoArchive.write(out, listOf("captura_abc123.jpg" to a, "captura_nohay.jpg" to missing, "../malo.jpg" to a))
        assertEquals(1, written)
        val read = mutableMapOf<String, ByteArray>()
        assertEquals(1, PhotoArchive.read(ByteArrayInputStream(out.toByteArray())) { name, bytes -> read[name] = bytes })
        assertArrayEquals(byteArrayOf(1, 2, 3), read.getValue("captura_abc123.jpg"))
    }

    @Test
    fun `al restaurar se ignora lo que no es una foto de la app`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            listOf("fotos/../captura_x1.jpg", "otra/captura_x2.jpg", "fotos/virus.apk", "fotos/captura_ok1.jpg").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(byteArrayOf(9))
                zip.closeEntry()
            }
        }
        val names = mutableListOf<String>()
        PhotoArchive.read(ByteArrayInputStream(out.toByteArray())) { name, _ -> names += name }
        assertEquals(listOf("captura_ok1.jpg"), names)
        assertTrue(PhotoArchive.isPhotoName("captura_ok1.jpg"))
        assertFalse(PhotoArchive.isPhotoName("../captura_ok1.jpg"))
    }
}
