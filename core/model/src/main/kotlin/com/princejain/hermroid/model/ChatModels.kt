package com.princejain.hermroid.model

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL;

    companion object {
        fun fromWire(value: String?): ChatRole = when (value?.lowercase()) {
            "user" -> USER
            "assistant" -> ASSISTANT
            "tool" -> TOOL
            else -> SYSTEM
        }
    }
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val reasoning: String = "",
    val streaming: Boolean = false,
)

enum class SessionStatus { IDLE, STARTING, WAITING, WORKING }

data class HermesSession(
    val id: String,
    val title: String,
    val preview: String = "",
    val model: String? = null,
    val messageCount: Int = 0,
    val startedAt: Long = 0,
    val lastActive: Long = startedAt,
    val status: SessionStatus = SessionStatus.IDLE,
    val current: Boolean = false,
)

fun List<HermesSession>.forDisplay(): List<HermesSession> =
    sortedWith(compareByDescending<HermesSession> { it.current }.thenByDescending { it.lastActive })

data class HermesModel(
    val id: String,
    val provider: String,
    val current: Boolean = false,
    val authenticated: Boolean = true,
) {
    val label: String get() = id.substringAfterLast('/')
}

data class ToolActivity(
    val id: String,
    val name: String,
    val summary: String = "",
    val running: Boolean = true,
    val error: String? = null,
)

data class ChatStreamState(
    val messages: List<ChatMessage> = emptyList(),
    val tools: List<ToolActivity> = emptyList(),
    val thinking: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)
