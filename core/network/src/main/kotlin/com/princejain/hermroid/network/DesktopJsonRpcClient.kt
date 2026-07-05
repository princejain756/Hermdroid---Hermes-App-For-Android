package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class DesktopEvent(val type:String,val payload:Map<String,Any?>)
interface JsonRpcTransport {
    val events: SharedFlow<DesktopEvent>
    suspend fun request(method: String, params: Map<String, Any?> = emptyMap()): Any?
    fun disconnect()
}

class DesktopJsonRpcClient(private val address:ServerAddress) : JsonRpcTransport {
    private val client = OkHttpClient()
    private val moshi=Moshi.Builder().build(); private val adapter=moshi.adapter(Any::class.java); private val ids=AtomicLong(); private val pending=ConcurrentHashMap<String,CompletableDeferred<Any?>>()
    private val mutableEvents=MutableSharedFlow<DesktopEvent>(extraBufferCapacity=128); override val events=mutableEvents.asSharedFlow(); private var socket:WebSocket?=null; private var ready=CompletableDeferred<Unit>()
    suspend fun connect(ticket:String){ ready=CompletableDeferred(); val http=address.baseUrl.resolve("api/ws")!!.newBuilder().addQueryParameter("ticket",ticket).build(); val ws=http.newBuilder().scheme(if(http.isHttps)"wss" else "ws").build(); socket=client.newWebSocket(Request.Builder().url(ws).build(),Listener()); ready.await() }
    override suspend fun request(method:String,params:Map<String,Any?>):Any? { val id="a${ids.incrementAndGet()}"; val deferred=CompletableDeferred<Any?>();pending[id]=deferred;val json=adapter.toJson(mapOf("jsonrpc" to "2.0","id" to id,"method" to method,"params" to params)); if(socket?.send(json)!=true){pending.remove(id);error("WebSocket is closed")};return deferred.await() }
    override fun disconnect(){socket?.close(1000,"client close");socket=null;failPending("WebSocket closed")}
    @Suppress("UNCHECKED_CAST") private fun receive(text:String){ text.lineSequence().filter{it.isNotBlank()}.forEach{line-> val obj=adapter.fromJson(line) as? Map<String,Any?>?:return@forEach; val id=obj["id"]?.toString(); if(id!=null){ val d=pending.remove(id)?:return@forEach; val error=obj["error"] as? Map<String,Any?>; if(error!=null)d.completeExceptionally(ApiException(error["message"]?.toString()?:"RPC error")) else d.complete(obj["result"]);return@forEach }; if(obj["method"]=="event"){val p=obj["params"] as? Map<String,Any?>?:emptyMap();val type=p["type"]?.toString()?:"unknown";val payload=p["payload"] as? Map<String,Any?>?:emptyMap();if(type=="gateway.ready"&&!ready.isCompleted)ready.complete(Unit);mutableEvents.tryEmit(DesktopEvent(type,payload))} } }
    private fun failPending(message:String){pending.values.forEach{it.completeExceptionally(ApiException(message))};pending.clear();if(!ready.isCompleted)ready.completeExceptionally(ApiException(message))}
    private inner class Listener:WebSocketListener(){override fun onMessage(webSocket:WebSocket,text:String)=receive(text);override fun onFailure(webSocket:WebSocket,t:Throwable,response:Response?)=failPending(t.message?:"WebSocket failed");override fun onClosed(webSocket:WebSocket,code:Int,reason:String)=failPending("WebSocket closed ($code)")}
}
