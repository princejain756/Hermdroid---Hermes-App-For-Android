package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HermesConnectionManagerTest {
    @Test
    fun `attach publishes Desktop connection and disconnect closes transport`() {
        val manager = HermesConnectionManager()
        val transport = RecordingTransport()
        val address = ServerAddress.parse("https://hermes.example.com")

        manager.attachDesktop(address, transport)

        val connected = manager.state.value as HermesConnectionState.Connected
        assertEquals(address, connected.address)
        assertTrue(connected.api is DesktopHermesApi)

        manager.disconnect()

        assertTrue(manager.state.value is HermesConnectionState.Disconnected)
        assertTrue(transport.disconnected)
    }
}

private class RecordingTransport : JsonRpcTransport {
    override val events = MutableSharedFlow<DesktopEvent>()
    var disconnected = false
    override suspend fun request(method: String, params: Map<String, Any?>): Any? = emptyMap<String, Any?>()
    override fun disconnect() { disconnected = true }
}
