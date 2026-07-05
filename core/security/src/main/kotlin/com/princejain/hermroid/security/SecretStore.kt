package com.princejain.hermroid.security
interface SecretStore { suspend fun put(serverId: String, name: String, value: String); suspend fun get(serverId: String, name: String): String?; suspend fun removeServer(serverId: String) }
