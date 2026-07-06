package com.princejain.hermroid.network

import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.HermesModel
import com.princejain.hermroid.model.HermesSession
import com.princejain.hermroid.model.ServerAddress
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class WebUiHermesApi(
    private val address: ServerAddress,
    private val client: OkHttpClient = OkHttpClient(),
) : HermesChatApi {
    private val moshi = Moshi.Builder().build()
    private val jsonAdapter = moshi.adapter(Any::class.java)
    private val mutableEvents = MutableSharedFlow<DesktopEvent>(extraBufferCapacity = 256)
    override val events = mutableEvents.asSharedFlow()
    private val sessions = ConcurrentHashMap<String, WebUiSession>()
    private val providers = ConcurrentHashMap<String, String>()
    private val streams = ConcurrentHashMap<String, Call>()

    override suspend fun listSessions(): List<HermesSession> {
        val root = get("api/sessions")
        return root.list("sessions").mapNotNull { raw ->
            val item = raw.mapValue() ?: return@mapNotNull null
            val id = item.string("session_id") ?: return@mapNotNull null
            HermesSession(
                id = id,
                title = item.string("title").orEmpty().ifBlank { "Untitled" },
                preview = item.string("preview", "last_message_preview").orEmpty(),
                model = item.string("model"),
                messageCount = item.int("message_count"),
                startedAt = item.long("created_at"),
                lastActive = item.long("updated_at", "last_message_at"),
            )
        }
    }

    override suspend fun createSession(columns: Int): String {
        val root = post("api/session/new", mapOf("profile" to "default"))
        val session = root.map("session")
        remember(session)
        return session.string("session_id") ?: throw ApiException("hermes-webui did not return a session id")
    }

    override suspend fun resumeSession(sessionId: String, columns: Int): SessionSnapshot {
        val root = get(
            "api/session",
            "session_id" to sessionId,
            "messages" to "1",
            "resolve_model" to "0",
        )
        val session = root.map("session")
        remember(session)
        val resolvedId = session.string("session_id") ?: sessionId
        return SessionSnapshot(
            sessionId = resolvedId,
            messages = session.list("messages").mapIndexedNotNull { index, raw ->
                val message = raw.mapValue() ?: return@mapIndexedNotNull null
                ChatMessage(
                    id = "$resolvedId-$index",
                    role = ChatRole.fromWire(message.string("role")),
                    text = message.contentText(),
                    reasoning = message.string("reasoning").orEmpty(),
                )
            },
            running = !session.string("active_stream_id").isNullOrBlank(),
        )
    }

    override suspend fun models(sessionId: String): List<HermesModel> {
        val current = sessions[sessionId]?.model
        return get("api/models").list("groups").flatMap { raw ->
            val group = raw.mapValue() ?: return@flatMap emptyList()
            val provider = group.string("provider_id", "provider").orEmpty()
            (group.list("models") + group.list("extra_models")).mapNotNull { modelRaw ->
                val model = modelRaw.mapValue() ?: return@mapNotNull null
                val id = model.string("id") ?: return@mapNotNull null
                providers[id] = provider
                HermesModel(id = id, provider = provider, current = id == current)
            }
        }
    }

    override suspend fun selectModel(sessionId: String, modelId: String): String {
        val provider = providers[modelId]
        val root = post(
            "api/session/update",
            mapOf("session_id" to sessionId, "model" to modelId, "model_provider" to provider),
        )
        val session = root.map("session")
        remember(session)
        return session.string("model") ?: modelId
    }

    override suspend fun submit(sessionId: String, text: String): Boolean {
        val state = sessions[sessionId] ?: WebUiSession()
        val root = post(
            "api/chat/start",
            mapOf(
                "session_id" to sessionId,
                "message" to text,
                "model" to state.model,
                "model_provider" to state.provider,
                "workspace" to state.workspace,
                "profile" to "default",
            ),
        )
        val streamId = root.string("stream_id") ?: throw ApiException("hermes-webui did not start a response stream")
        sessions[sessionId] = state.copy(streamId = streamId)
        openStream(sessionId, streamId)
        return true
    }

    override suspend fun interrupt(sessionId: String): Boolean {
        val streamId = sessions[sessionId]?.streamId ?: return false
        val result = get("api/chat/cancel", "stream_id" to streamId)
        return result.boolean("cancelled") || result.boolean("ok")
    }

    override suspend fun respondToApproval(sessionId: String, choice: String, requestId: String?): Boolean =
        post(
            "api/approval/respond",
            mapOf("session_id" to sessionId, "choice" to choice, "approval_id" to requestId),
        ).boolean("ok")

    override suspend fun respondToClarification(sessionId: String, requestId: String, answer: String): Boolean =
        post(
            "api/clarify/respond",
            mapOf("session_id" to sessionId, "response" to answer, "clarify_id" to requestId),
        ).boolean("ok")

    override fun disconnect() {
        streams.values.forEach(Call::cancel)
        streams.clear()
    }

    internal fun seedSessionForTest(sessionId: String, model: String?, provider: String?, workspace: String?) {
        sessions[sessionId] = WebUiSession(model, provider, workspace)
    }

    private fun openStream(sessionId: String, streamId: String) {
        val url = url("api/chat/stream", "stream_id" to streamId)
        val call = client.newCall(Request.Builder().url(url).header("Accept", "text/event-stream").build())
        streams[streamId] = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                streams.remove(streamId)
                if (!call.isCanceled()) emit("error", mapOf("message" to (e.message ?: "Stream disconnected")), sessionId)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        emit("error", mapOf("message" to "Stream returned ${response.code}"), sessionId)
                        return
                    }
                    response.body?.source()?.let { source -> consumeSse(source, sessionId) }
                }
                streams.remove(streamId)
            }
        })
    }

    private fun consumeSse(source: BufferedSource, sessionId: String) {
        var event = "message"
        val data = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.startsWith("event:") -> event = line.substringAfter(':').trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.substringAfter(':').trimStart())
                }
                line.isEmpty() -> {
                    if (data.isNotEmpty()) handleSse(event, data.toString(), sessionId)
                    event = "message"
                    data.clear()
                }
            }
        }
        if (data.isNotEmpty()) handleSse(event, data.toString(), sessionId)
    }

    private fun handleSse(event: String, json: String, sessionId: String) {
        val data = runCatching { jsonAdapter.fromJson(json).mapValue() }.getOrNull() ?: emptyMap()
        when (event) {
            "token" -> emit("message.delta", mapOf("text" to data.string("text").orEmpty()), sessionId)
            "reasoning" -> emit("reasoning.delta", mapOf("text" to data.string("text").orEmpty()), sessionId)
            "tool" -> emit("tool.start", data.normalizedTool(), sessionId)
            "tool_complete" -> emit("tool.complete", data.normalizedTool(), sessionId)
            "approval" -> emit("approval.request", data, sessionId)
            "clarify" -> emit("clarify.request", data, sessionId)
            "done" -> {
                data.mapOrNull("session")?.let(::remember)
                val final = data.mapOrNull("session")?.list("messages")
                    ?.asReversed()
                    ?.firstNotNullOfOrNull { raw -> raw.mapValue()?.takeIf { it.string("role") == "assistant" } }
                emit(
                    "message.complete",
                    mapOf("text" to (final?.contentText().orEmpty()), "reasoning" to final?.string("reasoning").orEmpty()),
                    sessionId,
                )
            }
            "apperror", "error", "cancel" -> emit("error", mapOf("message" to data.string("message", "error").orEmpty()), sessionId)
        }
    }

    private fun emit(type: String, payload: Map<String, Any?>, sessionId: String) {
        mutableEvents.tryEmit(DesktopEvent(type, payload, sessionId))
    }

    private fun remember(session: Map<String, Any?>) {
        val id = session.string("session_id") ?: return
        val old = sessions[id] ?: WebUiSession()
        sessions[id] = old.copy(
            model = session.string("model") ?: old.model,
            provider = session.string("model_provider") ?: old.provider,
            workspace = session.string("workspace") ?: old.workspace,
            streamId = session.string("active_stream_id") ?: old.streamId,
        )
    }

    private suspend fun get(path: String, vararg query: Pair<String, String>): Map<String, Any?> =
        execute(Request.Builder().url(url(path, *query)).get().build())

    private suspend fun post(path: String, body: Map<String, Any?>): Map<String, Any?> {
        val json = jsonAdapter.toJson(body)
        return execute(
            Request.Builder().url(url(path)).post(json.toRequestBody("application/json".toMediaType())).build(),
        )
    }

    private suspend fun execute(request: Request): Map<String, Any?> = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { jsonAdapter.fromJson(body).mapValue()?.string("error", "message") }.getOrNull()
                throw ApiException(message?.ifBlank { null } ?: "Server returned ${response.code}", response.code)
            }
            jsonAdapter.fromJson(body).mapValue() ?: throw ApiException("Server returned an invalid response")
        }
    }

    private fun url(path: String, vararg query: Pair<String, String>): HttpUrl {
        val builder = address.baseUrl.resolve(path)?.newBuilder() ?: throw ApiException("Invalid server URL")
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        return builder.build()
    }
}

private data class WebUiSession(
    val model: String? = null,
    val provider: String? = null,
    val workspace: String? = null,
    val streamId: String? = null,
)

private fun Any?.mapValue(): Map<String, Any?>? = this as? Map<String, Any?>
private fun Map<String, Any?>.map(key: String): Map<String, Any?> =
    mapOrNull(key) ?: throw ApiException("Server response is missing $key")
private fun Map<String, Any?>.mapOrNull(key: String): Map<String, Any?>? = this[key].mapValue()
private fun Map<String, Any?>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
private fun Map<String, Any?>.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { this[it] as? String }
private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.long(vararg keys: String): Long = keys.firstNotNullOfOrNull { (this[it] as? Number)?.toLong() } ?: 0
private fun Map<String, Any?>.boolean(key: String): Boolean = this[key] as? Boolean ?: false
private fun Map<String, Any?>.contentText(): String = when (val content = this["content"]) {
    is String -> content
    is List<*> -> content.mapNotNull { (it as? Map<*, *>)?.get("text") as? String }.joinToString("\n")
    else -> string("text").orEmpty()
}
private fun Map<String, Any?>.normalizedTool(): Map<String, Any?> = this + mapOf(
    "tool_id" to string("tool_id", "id").orEmpty(),
    "name" to string("name", "tool").orEmpty(),
    "args_text" to (this["args"]?.toString() ?: string("arguments").orEmpty()),
    "result_text" to (this["result"]?.toString() ?: string("summary").orEmpty()),
)
