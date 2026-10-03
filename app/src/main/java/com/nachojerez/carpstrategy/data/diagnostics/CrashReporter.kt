package com.nachojerez.carpstrategy.data.diagnostics

import android.content.Context
import android.os.Build
import com.nachojerez.carpstrategy.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda el error que cierra la app para enseñarlo al abrirla de nuevo (no sale del móvil:
 * el usuario decide si lo copia). Sin Google Play no hay otro modo de ver los cierres.
 */
@Singleton
class CrashReporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val file: File get() = File(context.filesDir, FILE_NAME)

    /** Se llama una vez al arrancar la app. Conserva el manejador de Android (que cierra la app). */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(report(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Informe del último cierre, o null si no lo hay. */
    fun lastReport(): String? = runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun report(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        return buildString {
            appendLine("CarpStrategy ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("${Instant.now()} · hilo ${thread.name}")
            appendLine()
            append(trace.take(MAX_CHARS))
        }
    }

    companion object {
        private const val FILE_NAME = "ultimo-cierre.txt"
        private const val MAX_CHARS = 12_000
    }
}
