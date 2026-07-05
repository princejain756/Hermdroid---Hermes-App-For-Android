package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import com.princejain.hermroid.model.ServerProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed interface DetectionResult { data class Detected(val protocol:ServerProtocol):DetectionResult; data object Ambiguous:DetectionResult; data class Unreachable(val message:String):DetectionResult }

class ProtocolDetector {
    private val client=OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(5,TimeUnit.SECONDS).followRedirects(false).build()
    suspend fun detect(address:ServerAddress):DetectionResult=withContext(Dispatchers.IO){
        try {
            val desktop=probe(address,"api/auth/providers")
            if((desktop.code==200 && desktop.body.contains("\"providers\"")) || (desktop.code==503 && desktop.body.contains("auth providers"))) return@withContext DetectionResult.Detected(ServerProtocol.DESKTOP)
            val auth=probe(address,"api/auth/status")
            val health=probe(address,"health")
            if(auth.code==200 && auth.body.contains("auth_enabled") && health.code==200 && health.body.contains("\"status\"")) DetectionResult.Detected(ServerProtocol.WEB_UI) else DetectionResult.Ambiguous
        } catch(e:Exception){ DetectionResult.Unreachable(e.message?:"Unable to reach server") }
    }
    private fun probe(address:ServerAddress,path:String):Probe { val req=Request.Builder().url(address.baseUrl.resolve(path)!!).get().build(); client.newCall(req).execute().use{return Probe(it.code,it.body?.string()?.take(16_384).orEmpty())} }
    private data class Probe(val code:Int,val body:String)
}
