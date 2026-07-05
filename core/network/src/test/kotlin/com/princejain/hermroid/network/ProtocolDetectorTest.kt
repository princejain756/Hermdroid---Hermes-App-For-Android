package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import com.princejain.hermroid.model.ServerProtocol
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProtocolDetectorTest {
    private lateinit var server: MockWebServer
    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test fun detectsDesktop() = runBlocking {
        server.enqueue(MockResponse().setBody("{\"providers\":[]}"))
        val result = ProtocolDetector().detect(ServerAddress.parse(server.url("/").toString()))
        assertEquals(ServerProtocol.DESKTOP, (result as DetectionResult.Detected).protocol)
        assertEquals("/api/auth/providers", server.takeRequest().path)
    }

    @Test fun detectsWebUiAfterDesktopMiss() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("{\"auth_enabled\":false}"))
        server.enqueue(MockResponse().setBody("{\"status\":\"ok\"}"))
        val result = ProtocolDetector().detect(ServerAddress.parse(server.url("/").toString()))
        assertEquals(ServerProtocol.WEB_UI, (result as DetectionResult.Detected).protocol)
    }
}
