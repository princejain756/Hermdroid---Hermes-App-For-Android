package com.princejain.hermroid.model

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@JvmInline value class ServerAddress private constructor(val baseUrl: HttpUrl) {
    companion object {
        fun parse(raw: String): ServerAddress {
            val value = raw.trim().let { if ("://" in it) it else "https://$it" }
            val url = value.toHttpUrlOrNull() ?: throw InvalidServerAddress("Enter a valid server URL")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) throw InvalidServerAddress("Put credentials in the password field")
            if (url.query != null || url.fragment != null) throw InvalidServerAddress("Remove query parameters and fragments")
            if (url.scheme == "http" && !url.isSafeCleartext()) throw InvalidServerAddress("Public servers must use HTTPS")
            return ServerAddress(url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build())
        }
    }
}
class InvalidServerAddress(message: String) : IllegalArgumentException(message)
private fun HttpUrl.isSafeCleartext(): Boolean {
    if (host in setOf("localhost", "127.0.0.1", "10.0.2.2")) return true
    val parts = host.split('.').mapNotNull(String::toIntOrNull)
    return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127 && parts.drop(2).all { it in 0..255 }
}
