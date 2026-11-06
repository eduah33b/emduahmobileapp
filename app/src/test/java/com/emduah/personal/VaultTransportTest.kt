package com.emduah.personal

import com.emduah.personal.data.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class VaultTransportTest {
    private fun withServer(block: (MockWebServer, VaultApi) -> Unit) {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.start()
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).followRedirects(false).build()
            block(server, VaultApi(client = client))
        }
    }
    @Test fun catalogRequiresBearerButStorageNeverReceivesIt() = withServer { server, api ->
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setBody("{}"))
        api.metadata(Connection(base, "private-api-token"))
        assertEquals("Bearer private-api-token", server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("media-content"))
        val file = File.createTempFile("vault-download", ".part")
        try {
            api.download(Selection(1, "test", "image", server.url("/storage").toString(), 0), file, 100)
            assertNull(server.takeRequest().getHeader("Authorization"))
            assertEquals("media-content", file.readText())
        } finally { file.delete() }
    }
    @Test fun enforcesLimitEvenWithoutContentLength() = withServer { server, api ->
        server.enqueue(MockResponse().setChunkedBody("x".repeat(2048), 32))
        val file = File.createTempFile("vault-limit", ".part")
        try { assertThrows(IllegalArgumentException::class.java) { api.download(Selection(1, "big", "video", server.url("/storage").toString(), 0), file, 1024) } }
        finally { file.delete() }
    }
    @Test fun rejectsAuthenticationRedirects() = withServer { server, api ->
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://untrusted.example/collect"))
        val error = assertThrows(ApiException::class.java) { api.metadata(Connection(server.url("/").toString(), "private-token")) }
        assertEquals(302, error.status)
        assertEquals(1, server.requestCount)
    }
    @Test fun stableShuffleSendsItsPersistedAnchorAndRequiresMatchingSeed() = withServer { server, api ->
        val base = server.url("/").toString().trimEnd('/')
        val data = """{"data":{"id":7,"name":"wallpaper","type":"image","downloadUrl":"https://storage.example/image","nextPosition":2,"seed":"chosen"}}"""
        server.enqueue(MockResponse().setBody(data))
        val profile = Profile(Screen.HOME, filters = """{"sort":"random","seed":"chosen"}""")
        val result = api.next(Connection(base, "private-token"), profile, CurrentMedia(position = 1, sequenceId = 42))
        assertEquals(7L, result.id)
        val body = org.json.JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(42L, body.getLong("after_id"))
        assertEquals("chosen", body.getString("seed"))
        server.enqueue(MockResponse().setBody(data.replace("\"seed\":\"chosen\"", "\"seed\":\"unexpected\"")))
        assertThrows(IllegalArgumentException::class.java) { api.next(Connection(base, "private-token"), profile, CurrentMedia()) }
    }

    @Test fun blockedNetworkPreventsStartingAMediaDownload() = withServer { server, api ->
        val file = File.createTempFile("vault-network", ".part")
        try {
            assertThrows(java.io.IOException::class.java) {
                api.download(Selection(1, "wallpaper", "video", server.url("/storage").toString(), 0), file, 1024) { false }
            }
            assertEquals(0, server.requestCount)
            assertEquals(0L, file.length())
        } finally { file.delete() }
    }

}
