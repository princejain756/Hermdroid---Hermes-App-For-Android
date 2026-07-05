package com.princejain.hermroid.network

import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.ChatStreamState
import com.princejain.hermroid.model.ToolActivity

class DesktopChatReducer(private val sessionId: String) {
    fun reduce(state: ChatStreamState, event: DesktopEvent): ChatStreamState {
        if (event.sessionId != null && event.sessionId != sessionId) return state

        return when (event.type) {
            "message.start" -> state.copy(
                messages = state.messages + ChatMessage(
                    id = "$sessionId-assistant-${state.messages.size}",
                    role = ChatRole.ASSISTANT,
                    text = "",
                    streaming = true,
                ),
                tools = emptyList(),
                thinking = "",
                busy = true,
                error = null,
            )
            "message.delta" -> state.updateAssistant { message ->
                message.copy(
                    text = message.text + event.payload.text("text", "rendered"),
                    streaming = true,
                )
            }
            "message.complete" -> state.updateAssistant { message ->
                val finalText = event.payload.text("rendered", "text")
                message.copy(
                    text = finalText.ifBlank { message.text },
                    reasoning = event.payload.text("reasoning").ifBlank { message.reasoning },
                    streaming = false,
                )
            }.copy(busy = false)
            "thinking.delta", "reasoning.delta" -> state.copy(
                thinking = state.thinking + event.payload.text("text"),
                busy = true,
            )
            "tool.start" -> {
                val id = event.payload.text("tool_id").ifBlank { "tool-${state.tools.size}" }
                state.copy(
                    tools = state.tools + ToolActivity(
                        id = id,
                        name = event.payload.text("name").ifBlank { "tool" },
                        summary = event.payload.text("args_text"),
                    ),
                    busy = true,
                )
            }
            "tool.complete" -> {
                val id = event.payload.text("tool_id")
                state.copy(tools = state.tools.map { tool ->
                    if (tool.id != id) tool else tool.copy(
                        summary = event.payload.text("summary", "result_text").ifBlank { tool.summary },
                        running = false,
                        error = event.payload["error"] as? String,
                    )
                })
            }
            "error" -> state.copy(
                busy = false,
                error = event.payload.text("message").ifBlank { "Hermes returned an error" },
            )
            else -> state
        }
    }
}

private fun ChatStreamState.updateAssistant(update: (ChatMessage) -> ChatMessage): ChatStreamState {
    val index = messages.indexOfLast { it.role == ChatRole.ASSISTANT && it.streaming }
    if (index >= 0) {
        return copy(messages = messages.toMutableList().also { it[index] = update(it[index]) })
    }
    val message = update(
        ChatMessage(
            id = "assistant-${messages.size}",
            role = ChatRole.ASSISTANT,
            text = "",
            streaming = true,
        ),
    )
    return copy(messages = messages + message)
}

private fun Map<String, Any?>.text(vararg keys: String): String =
    keys.firstNotNullOfOrNull { this[it] as? String }.orEmpty()
