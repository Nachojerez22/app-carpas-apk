package com.nachojerez.carpstrategy.data.sync

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DriveClientTest {
    private lateinit var server: MockWebServer
    private lateinit var drive: DriveClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        drive = DriveClient(OkHttpClient(), server.url("/drive/v3/"), server.url("/upload/drive/v3/"))
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `busca el archivo en la carpeta privada de la app con el token`() {
        server.enqueue(MockResponse(body = """{"files":[{"id":"abc","modifiedTime":"2026-10-04T10:00:00Z","appProperties":{"huella":"H1"}}]}"""))
        assertEquals(RemoteFile("abc", "2026-10-04T10:00:00Z", "H1"), drive.find("tok"))
        val request = server.takeRequest()
        assertEquals("Bearer tok", request.headers["Authorization"])
        assertEquals("appDataFolder", request.url.queryParameter("spaces"))
        assertTrue(request.url.queryParameter("q")!!.contains(DriveClient.FILE_NAME))
        server.enqueue(MockResponse(body = """{"files":[]}"""))
        assertNull(drive.find("tok"))
    }

    @Test
    fun `sube creando o sustituyendo y guarda la huella`() {
        server.enqueue(MockResponse(body = """{"id":"nuevo","appProperties":{"huella":"H2"}}"""))
        assertEquals("nuevo", drive.upload("tok", null, "{}", "H2").id)
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/upload/drive/v3/files", create.url.encodedPath)
        val body = create.body!!.utf8()
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"huella\":\"H2\""))
        server.enqueue(MockResponse(body = """{"id":"abc"}"""))
        drive.upload("tok", "abc", "{}", "H3")
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/upload/drive/v3/files/abc", update.url.encodedPath)
    }

    @Test
    fun `errores - permiso caducado y fallo del servidor`() {
        server.enqueue(MockResponse(code = 401))
        assertThrows<DriveException.Unauthorized> { drive.find("tok") }
        server.enqueue(MockResponse(code = 500))
        assertEquals(500, assertThrows<DriveException.Failed> { drive.download("tok", "abc") }.code)
    }
}
