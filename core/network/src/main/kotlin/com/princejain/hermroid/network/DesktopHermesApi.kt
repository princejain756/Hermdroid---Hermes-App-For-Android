package com.princejain.hermroid.network

import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.HermesModel
import com.princejain.hermroid.model.HermesSession

data class SessionSnapshot(
    val sessionId: String,
    val messages: List<ChatMessage>,
    val running: Boolean,
)

class DesktopHermesApi(private val rpc: JsonRpcTransport) : HermesChatApi {
    override val events = rpc.events

    override suspend fun listSessions(): List<HermesSession> {
        val result = rpc.request("session.list").objectValue()
        return result.list("sessions").mapNotNull { value ->
            val item = value as? Map<*, *> ?: return@mapNotNull null
            val id = item.string("id") ?: return@mapNotNull null
            HermesSession(
                id = id,
                title = item.string("title").orEmpty().ifBlank { id },
                preview = item.string("preview").orEmpty(),
                messageCount = item.int("message_count"),
                startedAt = item.long("started_at"),
            )
        }
    }

    override suspend fun createSession(columns: Int): String {
        val result = rpc.request("session.create", mapOf("cols" to columns)).objectValue()
        return result.string("session_id") ?: throw ApiException("Desktop did not return a session id")
    }

    override suspend fun resumeSession(sessionId: String, columns: Int): SessionSnapshot {
        val result = rpc.request(
            "session.resume",
            mapOf("session_id" to sessionId, "cols" to columns),
        ).objectValue()
        val resolvedId = result.string("session_id") ?: sessionId
        val messages = result.list("messages").mapIndexedNotNull { index, value ->
            val item = value as? Map<*, *> ?: return@mapIndexedNotNull null
            ChatMessage(
                id = "$resolvedId-$index",
                role = ChatRole.fromWire(item.string("role")),
                text = item.string("text").orEmpty(),
            )
        }
        return SessionSnapshot(
            sessionId = resolvedId,
            messages = messages,
            running = result["running"] as? Boolean ?: false,
        )
    }

    override suspend fun models(sessionId: String): List<HermesModel> {
        val result = rpc.request("model.options", mapOf("session_id" to sessionId)).objectValue()
        val current = result.string("model")
        return result.list("providers").flatMap { value ->
            val provider = value as? Map<*, *> ?: return@flatMap emptyList()
            val slug = provider.string("slug") ?: return@flatMap emptyList()
            val authenticated = provider["authenticated"] as? Boolean ?: false
            provider.list("models").mapNotNull { it as? String }.map { id ->
                HermesModel(
                    id = id,
                    provider = slug,
                    current = id == current,
                    authenticated = authenticated,
                )
            }
        }
    }

    override suspend fun selectModel(sessionId: String, modelId: String): String {
        val result = rpc.request(
            "config.set",
            mapOf("key" to "model", "session_id" to sessionId, "value" to modelId),
        ).objectValue()
        return result.string("value") ?: throw ApiException("Desktop rejected the model change")
    }

    override suspend fun submit(sessionId: String, text: String): Boolean =
        rpc.request("prompt.submit", mapOf("session_id" to sessionId, "text" to text))
            .objectValue()["ok"] as? Boolean ?: false

    override suspend fun interrupt(sessionId: String): Boolean =
        rpc.request("session.interrupt", mapOf("session_id" to sessionId))
            .objectValue()["ok"] as? Boolean ?: false

    override suspend fun respondToApproval(sessionId: String, choice: String, requestId: String?): Boolean =
        rpc.request("approval.respond", mapOf("choice" to choice, "session_id" to sessionId))
            .objectValue()["ok"] as? Boolean ?: false

    override suspend fun respondToClarification(sessionId: String, requestId: String, answer: String): Boolean =
        rpc.request("clarify.respond", mapOf("answer" to answer, "request_id" to requestId))
            .objectValue()["ok"] as? Boolean ?: false

    override fun disconnect() = rpc.disconnect()
}

@Suppress("UNCHECKED_CAST")
private fun Any?.objectValue(): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw ApiException("Desktop returned an invalid response")

private fun Map<*, *>.string(key: String): String? = this[key] as? String
private fun Map<*, *>.list(key: String): List<Any?> = this[key] as? List<Any?> ?: emptyList()
private fun Map<*, *>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0
private fun Map<*, *>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
