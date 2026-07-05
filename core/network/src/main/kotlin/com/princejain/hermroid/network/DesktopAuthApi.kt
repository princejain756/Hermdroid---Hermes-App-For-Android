package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@JsonClass(generateAdapter=true) data class DesktopProvider(@Json(name="name") val name:String,@Json(name="display_name") val displayName:String,@Json(name="supports_password") val supportsPassword:Boolean)
@JsonClass(generateAdapter=true) data class DesktopProviders(val providers:List<DesktopProvider>)
@JsonClass(generateAdapter=true) internal data class DesktopPasswordLogin(val provider:String,val username:String,val password:String,val next:String="")
@JsonClass(generateAdapter=true) data class DesktopLoginResult(val ok:Boolean,val next:String?)
@JsonClass(generateAdapter=true) data class WsTicket(val ticket:String,@Json(name="ttl_seconds") val ttlSeconds:Int)

class DesktopAuthApi(private val address:ServerAddress){
    private val moshi=Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val client=OkHttpClient.Builder().cookieJar(OriginCookieJar(address.baseUrl)).followRedirects(false).build()
    suspend fun providers():List<AuthProvider> = request("api/auth/providers",null,DesktopProviders::class.java).providers.map{AuthProvider(it.name,it.displayName,it.supportsPassword)}
    suspend fun login(provider:String,username:String,password:String):DesktopLoginResult { val body=moshi.adapter(DesktopPasswordLogin::class.java).toJson(DesktopPasswordLogin(provider,username,password)); return request("auth/password-login",body,DesktopLoginResult::class.java) }
    suspend fun ticket():WsTicket=request("api/auth/ws-ticket","{}",WsTicket::class.java)
    private suspend fun <T> request(path:String,json:String?,type:Class<T>):T=withContext(Dispatchers.IO){ val b=Request.Builder().url(address.baseUrl.resolve(path)!!).header("Accept","application/json"); if(json==null)b.get() else b.post(json.toRequestBody("application/json".toMediaType())); client.newCall(b.build()).execute().use{r-> val text=r.body?.string().orEmpty(); if(!r.isSuccessful)throw ApiException(if(r.code==401)"Invalid credentials" else if(r.code==429)"Too many attempts. Try again shortly." else "Desktop returned ${r.code}",r.code); moshi.adapter(type).fromJson(text)?:throw ApiException("Desktop returned an invalid response") } }
}
internal class OriginCookieJar(private val origin:HttpUrl):CookieJar { private val values=mutableListOf<Cookie>(); override fun saveFromResponse(url:HttpUrl,cookies:List<Cookie>){if(url.host!=origin.host)return; values.removeAll{old->cookies.any{it.name==old.name}};values+=cookies}; override fun loadForRequest(url:HttpUrl)=if(url.host==origin.host)values.filter{it.matches(url)} else emptyList() }
