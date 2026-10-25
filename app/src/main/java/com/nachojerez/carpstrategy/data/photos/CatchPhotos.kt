package com.nachojerez.carpstrategy.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import com.nachojerez.carpstrategy.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Fotos de las capturas (fase 7.17): solo en este móvil, en la carpeta privada de la app (no van
 * a la cuenta de Google ni a las exportaciones; la captura guarda el nombre del archivo). Se
 * reducen a [MAX_SIDE_PX] y se enderezan según la cámara, para que ocupen poco.
 */
@Singleton
class CatchPhotos @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun exists(name: String): Boolean = file(name)?.takeIf { it.isFile && it.length() > 0 } != null

    /** Archivo nuevo para la cámara del sistema: nombre y su `content://` (FileProvider). */
    fun captureTarget(): Pair<String, Uri> {
        val name = newName()
        val file = File(dir, name)
        return name to FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /** Tras la cámara: si hay foto, se reduce y endereza; si se canceló, se borra. */
    suspend fun finishCapture(name: String): String? = withContext(io) {
        val file = file(name) ?: return@withContext null
        if (!file.isFile || file.length() == 0L) {
            file.delete()
            return@withContext null
        }
        val rotation = file.inputStream().use(::rotationOf)
        val bitmap = decodeScaled({ file.inputStream() }) ?: run {
            file.delete()
            return@withContext null
        }
        save(rotate(bitmap, rotation), file)
        name
    }

    /** Desde la galería (selector de fotos): se copia reducida a la carpeta de la app. */
    suspend fun importFrom(uri: Uri): String? = withContext(io) {
        val resolver = context.contentResolver
        val rotation = runCatching { resolver.openInputStream(uri)?.use(::rotationOf) }.getOrNull() ?: 0
        val bitmap = decodeScaled({ resolver.openInputStream(uri) ?: error("sin datos") }) ?: return@withContext null
        val name = newName()
        save(rotate(bitmap, rotation), File(dir, name))
        name
    }

    /** Miniatura para la ficha (null si la foto no está en este móvil). */
    suspend fun thumbnail(name: String, maxSidePx: Int = THUMB_SIDE_PX): ImageBitmap? = withContext(io) {
        val file = file(name)?.takeIf { it.isFile } ?: return@withContext null
        decodeScaled({ file.inputStream() }, maxSidePx)?.asImageBitmap()
    }

    fun delete(name: String) {
        file(name)?.delete()
    }

    /** Solo nombres generados por la app: nada de rutas. */
    private fun file(name: String): File? = name.takeIf { NAME.matches(it) }?.let { File(dir, it) }

    private fun newName() = "captura_" + UUID.randomUUID().toString().replace("-", "").take(16) + ".jpg"

    private fun decodeScaled(open: () -> InputStream, maxSide: Int = MAX_SIDE_PX): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
        val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale >= 1f) decoded else Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    }.getOrNull()

    private fun rotationOf(input: InputStream): Int = when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap =
        if (degrees == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
    }

    companion object {
        const val DIR = "fotos"
        const val AUTHORITY_SUFFIX = ".fotos"
        const val MAX_SIDE_PX = 1600
        const val THUMB_SIDE_PX = 480
        private const val JPEG_QUALITY = 85
        private val NAME = Regex("captura_[a-z0-9]{1,32}\\.jpg")
    }
}
