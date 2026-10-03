package com.nachojerez.carpstrategy

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Android usa el motor de expresiones regulares de ICU, más estricto que el de la JVM: una
 * llave `}` sin escapar fuera de un cuantificador `{n,m}` lanza PatternSyntaxException al
 * arrancar la clase (cerraba Estrategia y Lugar). Los tests JVM no lo detectan, así que se
 * revisan los patrones del código fuente.
 */
class RegexAndroidCompatTest {
    private val regexLiteral = Regex("""Regex\(\s*(?:\"\"\"(.*?)\"\"\"|"([^"]*)")""")
    private val quantifier = Regex("""\{\d+(,\d*)?\}""")

    @Test
    fun `las llaves de las expresiones regulares van escapadas`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file ->
                regexLiteral.findAll(file.readText()).map { m -> file.name to (m.groupValues[1].ifEmpty { m.groupValues[2] }) }
            }
            .filter { (_, pattern) -> hasUnescapedBrace(pattern.replace(quantifier, "")) }
            .toList()
        assertTrue(offenders.isEmpty(), "Llaves sin escapar (fallan en Android): $offenders")
    }

    private fun hasUnescapedBrace(pattern: String): Boolean {
        var escaped = false
        for (c in pattern) {
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') escaped = true else if (c == '{' || c == '}') return true
        }
        return false
    }
}
