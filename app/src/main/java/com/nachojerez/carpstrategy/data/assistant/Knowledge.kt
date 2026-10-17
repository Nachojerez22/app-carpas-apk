package com.nachojerez.carpstrategy.data.assistant

/**
 * Extractos de CONOCIMIENTO.md (copia en `assets/conocimiento.md`) que se mandan a la IA para
 * que decida con el documento y no de memoria. Funciones puras.
 */
object Knowledge {
    const val ASSET = "conocimiento.md"

    /** Plan y repaso: estrategias (§5) y, solo en Brovales, sus datos (§6). */
    fun forPlan(md: String, brovales: Boolean = true): String = between(md, "## 5.", if (brovales) "## 7." else "## 6.")

    /** Avisos: el playbook (§5.8) y la sesión guiada (§5.9), más cortos. */
    fun forCheckIn(md: String): String = between(md, "### 5.8", "## 6.")

    /** Desde la línea que empieza por [from] hasta la que empieza por [to] (sin incluirla). */
    fun between(md: String, from: String, to: String): String {
        val lines = md.lines()
        val start = lines.indexOfFirst { it.startsWith(from) }.takeIf { it >= 0 } ?: return ""
        val end = lines.drop(start + 1).indexOfFirst { it.startsWith(to) }.let { if (it < 0) lines.size else start + 1 + it }
        return lines.subList(start, end).joinToString("\n").trim()
    }
}
