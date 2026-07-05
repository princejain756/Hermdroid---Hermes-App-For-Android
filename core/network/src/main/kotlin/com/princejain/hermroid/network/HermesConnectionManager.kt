package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HermesConnectionState {
    data object Disconnected : HermesConnectionState
    data class ConnectedDesktop(
        val address: ServerAddress,
        val transport: JsonRpcTransport,
        val api: DesktopHermesApi = DesktopHermesApi(transport),
    ) : HermesConnectionState
}

class HermesConnectionManager {
    private val mutableState = MutableStateFlow<HermesConnectionState>(HermesConnectionState.Disconnected)
    val state = mutableState.asStateFlow()

    fun attachDesktop(address: ServerAddress, transport: JsonRpcTransport) {
        disconnect()
        mutableState.value = HermesConnectionState.ConnectedDesktop(address, transport)
    }

    fun disconnect() {
        (mutableState.value as? HermesConnectionState.ConnectedDesktop)?.transport?.disconnect()
        mutableState.value = HermesConnectionState.Disconnected
    }
}

val DefaultHermesConnectionManager = HermesConnectionManager()
