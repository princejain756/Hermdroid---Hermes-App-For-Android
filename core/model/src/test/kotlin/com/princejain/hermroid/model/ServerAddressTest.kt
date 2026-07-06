package com.princejain.hermroid.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerAddressTest {
    @Test fun `allows cleartext only on loopback private lan and tailscale`() {
        listOf(
            "http://127.0.0.1:8080",
            "http://10.0.0.5:8080",
            "http://172.16.4.2:8080",
            "http://172.31.255.2:8080",
            "http://192.168.1.20:8080",
            "http://100.64.1.2:8080",
            "http://100.127.255.2:8080",
        ).forEach { assertTrue(ServerAddress.parse(it).baseUrl.toString().startsWith("http://")) }
    }

    @Test fun `rejects cleartext public and carrier addresses outside tailscale range`() {
        listOf("http://8.8.8.8", "http://example.com", "http://100.128.0.1").forEach { raw ->
            assertTrue(runCatching { ServerAddress.parse(raw) }.exceptionOrNull() is InvalidServerAddress)
        }
    }

    @Test fun `normalizes https path with trailing slash`() {
        assertEquals("https://example.com/hermes/", ServerAddress.parse("https://example.com/hermes").baseUrl.toString())
    }
}
