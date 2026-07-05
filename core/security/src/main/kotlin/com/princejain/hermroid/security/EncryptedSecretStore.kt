package com.princejain.hermroid.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64

class EncryptedSecretStore(context: Context) : SecretStore {
    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "hermroid_encrypted_secrets",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun put(serverId: String, name: String, value: String) = withContext(Dispatchers.IO) {
        val key = secretPreferenceKey(serverId, name)
        val indexKey = secretPreferenceKey(serverId, INDEX_NAME)
        val keys = preferences.getStringSet(indexKey, emptySet()).orEmpty().toMutableSet().apply { add(key) }
        check(preferences.edit().putString(key, value).putStringSet(indexKey, keys).commit()) {
            "Unable to save encrypted credentials"
        }
    }

    override suspend fun get(serverId: String, name: String): String? = withContext(Dispatchers.IO) {
        preferences.getString(secretPreferenceKey(serverId, name), null)
    }

    override suspend fun removeServer(serverId: String) = withContext(Dispatchers.IO) {
        val indexKey = secretPreferenceKey(serverId, INDEX_NAME)
        val editor = preferences.edit()
        preferences.getStringSet(indexKey, emptySet()).orEmpty().forEach(editor::remove)
        check(editor.remove(indexKey).commit()) { "Unable to remove encrypted credentials" }
    }

    private companion object { const val INDEX_NAME = "__index__" }
}

internal fun secretPreferenceKey(serverId: String, name: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("$serverId\u0000$name".toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
