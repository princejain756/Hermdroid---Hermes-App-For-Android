package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopJsonRpcClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun `connect returns after gateway ready`() = runBlocking {
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"jsonrpc":"2.0","method":"event","params":{"type":"gateway.ready","payload":{}}}""")
                }
            }),
        )
        val client = DesktopJsonRpcClient(
            ServerAddress.parse(server.url("/").toString()),
            OkHttpClient(),
            connectTimeoutMillis = 1_000,
        )

        client.connect("ticket")

        assertEquals("/api/ws?ticket=ticket", server.takeRequest().path)
        client.disconnect()
    }

    @Test fun `connect fails instead of hanging when ready never arrives`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))
        val client = DesktopJsonRpcClient(
            ServerAddress.parse(server.url("/").toString()),
            OkHttpClient(),
            connectTimeoutMillis = 50,
        )

        val failure = runCatching { client.connect("ticket") }.exceptionOrNull()

        assertTrue(failure is ApiException)
        assertTrue(failure?.message.orEmpty().contains("timed out"))
    }
}
