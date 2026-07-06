package com.princejain.hermroid.security

import android.content.Context

data class SavedConnection(
    val url: String,
    val protocol: String,
    val provider: String = "",
    val username: String = "",
    val password: String = "",
)

class EncryptedConnectionStore(context: Context) {
    private val secrets: SecretStore = EncryptedSecretStore(context)

    suspend fun save(connection: SavedConnection) {
        secrets.put(SLOT, URL, connection.url)
        secrets.put(SLOT, PROTOCOL, connection.protocol)
        secrets.put(SLOT, PROVIDER, connection.provider)
        secrets.put(SLOT, USERNAME, connection.username)
        secrets.put(SLOT, PASSWORD, connection.password)
    }

    suspend fun load(): SavedConnection? {
        val url = secrets.get(SLOT, URL)?.takeIf(String::isNotBlank) ?: return null
        return SavedConnection(
            url = url,
            protocol = secrets.get(SLOT, PROTOCOL).orEmpty(),
            provider = secrets.get(SLOT, PROVIDER).orEmpty(),
            username = secrets.get(SLOT, USERNAME).orEmpty(),
            password = secrets.get(SLOT, PASSWORD).orEmpty(),
        )
    }

    suspend fun clear() = secrets.removeServer(SLOT)

    private companion object {
        const val SLOT = "last_connection"
        const val URL = "url"
        const val PROTOCOL = "protocol"
        const val PROVIDER = "provider"
        const val USERNAME = "username"
        const val PASSWORD = "password"
    }
}
