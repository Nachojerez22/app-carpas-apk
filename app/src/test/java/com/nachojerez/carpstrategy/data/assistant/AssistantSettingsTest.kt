package com.nachojerez.carpstrategy.data.assistant

import com.nachojerez.carpstrategy.data.userdata.LocalSettings
import com.nachojerez.carpstrategy.data.userdata.SettingDao
import com.nachojerez.carpstrategy.data.userdata.SettingEntity
import com.nachojerez.carpstrategy.domain.guided.AiCheckInMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssistantSettingsTest {
    /** Tabla de ajustes en memoria; [copyAccountTo] simula la sincronización con Drive (solo lo que no es `local_`). */
    private class FakeDao : SettingDao {
        val rows = MutableStateFlow<Map<String, String>>(emptyMap())
        override fun observe(key: String): Flow<String?> = rows.map { it[key] }
        override suspend fun upsert(setting: SettingEntity) { rows.value = rows.value + (setting.key to setting.value) }
        override fun observeAll(): Flow<List<SettingEntity>> = rows.map { m -> m.map { SettingEntity(it.key, it.value) } }
        override suspend fun all(): List<SettingEntity> = rows.value.map { SettingEntity(it.key, it.value) }
        override suspend fun delete(key: String) { rows.value = rows.value - key }

        fun copyAccountTo(other: FakeDao) {
            other.rows.value = other.rows.value.filterKeys { it.startsWith("local_") } + rows.value.filterKeys { !it.startsWith("local_") }
        }
    }

    private fun settings(dao: FakeDao) = AssistantSettings(LocalSettings(dao), dao)

    @Test
    fun `la clave en la cuenta llega al otro movil y nunca va como ajuste local`() = runTest {
        val phoneA = FakeDao()
        val phoneB = FakeDao()
        settings(phoneA).setApiKey("  AIzaClaveDePrueba1234 ", inAccount = true)
        settings(phoneA).setConfig(AiConfig(model = "gemini-x"))
        assertEquals("AIzaClaveDePrueba1234", phoneA.rows.value["local_ai_key"])
        assertEquals("AIzaClaveDePrueba1234", phoneA.rows.value[AssistantSettings.ACCOUNT_KEY])

        phoneA.copyAccountTo(phoneB)
        val b = settings(phoneB)
        assertEquals("AIzaClaveDePrueba1234", b.apiKey())
        assertEquals("••••1234", b.observeKeyHint().first())
        assertTrue(b.observeInAccount().first())
        assertEquals("gemini-x", b.config().model)
        assertNull(phoneB.rows.value["local_ai_key"])
    }

    @Test
    fun `solo en este movil, desactivar y borrar`() = runTest {
        val dao = FakeDao()
        val s = settings(dao)
        s.setApiKey("clave-local-0001", inAccount = false)
        assertFalse(s.observeInAccount().first())
        assertNull(dao.rows.value[AssistantSettings.ACCOUNT_KEY])

        s.setInAccount(true)
        assertEquals("clave-local-0001", dao.rows.value[AssistantSettings.ACCOUNT_KEY])
        // Desactivar deja la copia en el móvil.
        s.setInAccount(false)
        assertNull(dao.rows.value[AssistantSettings.ACCOUNT_KEY])
        assertEquals("clave-local-0001", s.apiKey())

        s.setInAccount(true)
        s.deleteApiKey()
        assertNull(s.apiKey())
        assertNull(dao.rows.value[AssistantSettings.ACCOUNT_KEY])
    }

    @Test
    fun `manda la clave de la cuenta y el ajuste antiguo del movil sigue valiendo`() = runTest {
        val dao = FakeDao()
        dao.upsert(SettingEntity("local_ai_key", "antigua-0000"))
        dao.upsert(SettingEntity("local_ai_config", """{"modelo":"modelo-antiguo"}"""))
        val s = settings(dao)
        assertEquals("antigua-0000", s.apiKey())
        assertEquals("modelo-antiguo", s.config().model)
        dao.upsert(SettingEntity(AssistantSettings.ACCOUNT_KEY, "nueva-9999"))
        assertEquals("nueva-9999", s.apiKey())
    }

    @Test
    fun `modo de los avisos y el ajuste antiguo en cada aviso`() = runTest {
        val dao = FakeDao()
        val s = settings(dao)
        assertEquals(AiCheckInMode.ON_CHANGE, s.config().checkInMode)
        // Antes de la 0.21.0: «en cada aviso» sí ⇒ ahora «si cambia algo»; no ⇒ nunca.
        dao.upsert(SettingEntity(AssistantSettings.ACCOUNT_CONFIG, """{"modelo":"m","en_cada_aviso":true}"""))
        assertEquals(AiCheckInMode.ON_CHANGE, s.config().checkInMode)
        dao.upsert(SettingEntity(AssistantSettings.ACCOUNT_CONFIG, """{"modelo":"m","en_cada_aviso":false}"""))
        assertEquals(AiCheckInMode.NEVER, s.config().checkInMode)
        s.setConfig(AiConfig(model = "m", checkInMode = AiCheckInMode.ALWAYS))
        assertEquals(AiCheckInMode.ALWAYS, s.config().checkInMode)
        assertTrue(dao.rows.value[AssistantSettings.ACCOUNT_CONFIG]!!.contains("\"modo_avisos\":\"ALWAYS\""))
        // Una versión anterior en el otro móvil lee «nunca» como «en cada aviso: no».
        s.setConfig(AiConfig(model = "m", checkInMode = AiCheckInMode.NEVER))
        assertTrue(dao.rows.value[AssistantSettings.ACCOUNT_CONFIG]!!.contains("\"en_cada_aviso\":false"))
    }
}
