package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true) data class Health(val status: String?)
@JsonClass(generateAdapter = true) data class AuthStatus(@Json(name="auth_enabled") val authEnabled: Boolean?, @Json(name="password_auth_enabled") val passwordAuthEnabled: Boolean?)
@JsonClass(generateAdapter = true) data class LoginResult(val ok: Boolean?)
@JsonClass(generateAdapter = true) internal data class LoginBody(val password: String)

class AuthApi(private val address: ServerAddress) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client = OkHttpClient.Builder().cookieJar(MemoryCookieJar()).followRedirects(false).build()

    suspend fun health(): Health = get("health")
    suspend fun authStatus(): AuthStatus = get("api/auth/status")
    suspend fun login(password: String): LoginResult = post("api/auth/login", moshi.adapter(LoginBody::class.java).toJson(LoginBody(password)))
    fun chatApi(): WebUiHermesApi = WebUiHermesApi(address, client)

    private suspend inline fun <reified T> get(path: String): T = execute(path, null)
    private suspend inline fun <reified T> post(path: String, json: String): T = execute(path, json)
    private suspend inline fun <reified T> execute(path: String, json: String?): T = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(address.baseUrl.resolve(path)!!).header("Accept", "application/json")
        if (json == null) builder.get() else builder.post(json.toRequestBody("application/json".toMediaType()))
        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 401) throw ApiException("Incorrect password", 401)
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException("Server returned ${response.code}", response.code)
            moshi.adapter(T::class.java).fromJson(body) ?: throw ApiException("Server returned an invalid response")
        }
    }
}
class ApiException(message: String, val status: Int? = null) : Exception(message)
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies.removeAll { old -> cookies.any { it.name == old.name } }; this.cookies += cookies }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
}
