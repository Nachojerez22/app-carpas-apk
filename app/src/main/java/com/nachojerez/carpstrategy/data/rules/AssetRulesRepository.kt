package com.nachojerez.carpstrategy.data.rules

import android.content.Context
import com.nachojerez.carpstrategy.di.IoDispatcher
import com.nachojerez.carpstrategy.domain.rules.RuleLoadResult
import com.nachojerez.carpstrategy.domain.rules.RulesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Carga y valida assets/rules.json una sola vez. */
@Singleton
class AssetRulesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : RulesRepository {
    private val mutex = Mutex()
    private var cached: RuleLoadResult? = null

    override suspend fun load(): RuleLoadResult = mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            val text = context.assets.open(ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
            RulesJson.parse(text)
        }.also { cached = it }
    }

    companion object {
        const val ASSET = "rules.json"
    }
}
