package com.nachojerez.carpstrategy.data.assistant

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lee una vez la copia de CONOCIMIENTO.md que va dentro de la app. */
@Singleton
class KnowledgeAsset @Inject constructor(@param:ApplicationContext private val context: Context) {
    val text: String? by lazy { runCatching { context.assets.open(Knowledge.ASSET).bufferedReader().use { it.readText() } }.getOrNull() }

    fun forPlan(brovales: Boolean = true): String? = text?.let { Knowledge.forPlan(it, brovales) }

    fun forCheckIn(): String? = text?.let(Knowledge::forCheckIn)
}
