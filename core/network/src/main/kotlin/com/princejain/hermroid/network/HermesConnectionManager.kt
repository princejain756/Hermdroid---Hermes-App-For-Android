package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface HermesConnectionState {
    data object Disconnected : HermesConnectionState
    data class Connected(
        val address: ServerAddress,
        val api: HermesChatApi,
    ) : HermesConnectionState
}

class HermesConnectionManager {
    private val mutableState = MutableStateFlow<HermesConnectionState>(HermesConnectionState.Disconnected)
    val state = mutableState.asStateFlow()

    fun attachDesktop(address: ServerAddress, transport: JsonRpcTransport) {
        disconnect()
        mutableState.value = HermesConnectionState.Connected(address, DesktopHermesApi(transport))
    }

    fun attachWebUi(address: ServerAddress, api: WebUiHermesApi) {
        disconnect()
        mutableState.value = HermesConnectionState.Connected(address, api)
    }

    fun disconnect() {
        (mutableState.value as? HermesConnectionState.Connected)?.api?.disconnect()
        mutableState.value = HermesConnectionState.Disconnected
    }
}

val DefaultHermesConnectionManager = HermesConnectionManager()
