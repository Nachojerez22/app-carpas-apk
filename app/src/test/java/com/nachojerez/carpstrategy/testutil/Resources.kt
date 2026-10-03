package com.nachojerez.carpstrategy.testutil

import java.nio.charset.Charset

object Resources {
    fun bytes(path: String): ByteArray =
        requireNotNull(Resources::class.java.getResource(path)) { "Recurso no encontrado: $path" }.readBytes()

    fun text(path: String, charset: Charset = Charsets.UTF_8): String = String(bytes(path), charset)
}
