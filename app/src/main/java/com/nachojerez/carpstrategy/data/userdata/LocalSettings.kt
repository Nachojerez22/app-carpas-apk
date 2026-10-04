package com.nachojerez.carpstrategy.data.userdata

import com.nachojerez.carpstrategy.data.sync.SyncSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Ajustes que se quedan en este móvil y nunca se sincronizan (prefijo `local_`): estado de la
 * cuenta de Google, avisos de versión descartados…
 */
@Singleton
class LocalSettings @Inject constructor(private val dao: SettingDao) {
    fun observe(key: String): Flow<String?> = dao.observe(prefixed(key))

    suspend fun get(key: String): String? = dao.observe(prefixed(key)).first()

    suspend fun put(key: String, value: String?) {
        if (value == null) dao.delete(prefixed(key)) else dao.upsert(SettingEntity(prefixed(key), value))
    }

    private fun prefixed(key: String) = if (SyncSnapshot.isLocalOnly(key)) key else SyncSnapshot.LOCAL_PREFIX + key
}
