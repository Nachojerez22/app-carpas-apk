package com.nachojerez.carpstrategy.data.update

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpdateCheckerTest {
    private val release = """
        {"tag_name":"v0.8.2-52","body":"Fase 7.2","draft":false,"prerelease":false,
         "assets":[{"name":"CarpStrategy.apk","browser_download_url":"https://github.com/x/CarpStrategy.apk"}]}
    """.trimIndent()

    @Test
    fun `etiqueta, version mas nueva y APK`() {
        assertEquals("0.8.2" to 52, UpdateChecker.parseTag("v0.8.2-52"))
        assertNull(UpdateChecker.parseTag("0.8.2"))
        val update = UpdateChecker(OkHttpClient()).parse(release)!!
        assertEquals(52, update.versionCode)
        assertEquals("https://github.com/x/CarpStrategy.apk", update.downloadUrl)
        assertNull(UpdateChecker(OkHttpClient()).parse(release.replace("\"draft\":false", "\"draft\":true")))
    }

    @Test
    fun `solo avisa si es mas nueva y calla sin red o con error`() {
        MockWebServer().use { server ->
            server.start()
            val checker = UpdateChecker(OkHttpClient(), server.url("/latest"))
            server.enqueue(MockResponse(body = release))
            assertEquals(52, checker.check(49)!!.versionCode)
            server.enqueue(MockResponse(body = release))
            assertNull(checker.check(52))
            server.enqueue(MockResponse(code = 404))
            assertNull(checker.check(1))
        }
    }
}
