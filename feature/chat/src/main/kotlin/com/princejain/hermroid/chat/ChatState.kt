package com.princejain.hermroid.chat

import com.princejain.hermroid.automation.AndroidAction
import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.ChatStreamState
import com.princejain.hermroid.model.HermesModel
import com.princejain.hermroid.model.HermesSession
import com.princejain.hermroid.model.ToolActivity
import com.princejain.hermroid.model.forDisplay

data class ApprovalRequest(
    val command: String,
    val description: String,
    val allowPermanent: Boolean,
    val requestId: String? = null,
)

data class ClarificationRequest(
    val requestId: String,
    val question: String,
    val choices: List<String>,
)

data class ChatUiState(
    val sessions: List<HermesSession> = emptyList(),
    val currentSessionId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val models: List<HermesModel> = emptyList(),
    val selectedModelId: String? = null,
    val tools: List<ToolActivity> = emptyList(),
    val thinking: String = "",
    val draft: String = "",
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val approval: ApprovalRequest? = null,
    val clarification: ClarificationRequest? = null,
    val pendingAndroidAction: AndroidAction? = null,
)

sealed interface ChatAction {
    data class SessionsLoaded(val sessions: List<HermesSession>) : ChatAction
    data class SessionOpened(
        val sessionId: String,
        val messages: List<ChatMessage>,
        val models: List<HermesModel>,
        val running: Boolean = false,
    ) : ChatAction
    data class DraftChanged(val value: String) : ChatAction
    data class SendStarted(val messageId: String) : ChatAction
    data class StreamUpdated(val stream: ChatStreamState) : ChatAction
    data class ModelSelected(val modelId: String) : ChatAction
    data class ApprovalRequested(val request: ApprovalRequest) : ChatAction
    data class ClarificationRequested(val request: ClarificationRequest) : ChatAction
    data object RequestAnswered : ChatAction
    data class AndroidActionRequested(val action: AndroidAction) : ChatAction
    data class AndroidActionCompleted(val description: String, val messageId: String) : ChatAction
    data object AndroidActionCancelled : ChatAction
    data object Interrupted : ChatAction
    data class Failed(val message: String) : ChatAction
}

fun reduceChatState(state: ChatUiState, action: ChatAction): ChatUiState = when (action) {
    is ChatAction.SessionsLoaded -> state.copy(
        sessions = action.sessions.forDisplay(),
        loading = false,
        error = null,
    )
    is ChatAction.SessionOpened -> state.copy(
        currentSessionId = action.sessionId,
        messages = action.messages,
        models = action.models,
        selectedModelId = action.models.firstOrNull { it.current }?.id,
        tools = emptyList(),
        thinking = "",
        loading = false,
        busy = action.running,
        error = null,
    )
    is ChatAction.DraftChanged -> state.copy(draft = action.value)
    is ChatAction.SendStarted -> {
        val text = state.draft.trim()
        if (text.isEmpty()) state else state.copy(
            messages = state.messages + ChatMessage(action.messageId, ChatRole.USER, text),
            draft = "",
            busy = true,
            error = null,
        )
    }
    is ChatAction.StreamUpdated -> state.copy(
        messages = action.stream.messages,
        tools = action.stream.tools,
        thinking = action.stream.thinking,
        busy = action.stream.busy,
        error = action.stream.error,
    )
    is ChatAction.ModelSelected -> state.copy(selectedModelId = action.modelId, error = null)
    is ChatAction.ApprovalRequested -> state.copy(approval = action.request)
    is ChatAction.ClarificationRequested -> state.copy(clarification = action.request)
    ChatAction.RequestAnswered -> state.copy(approval = null, clarification = null)
    is ChatAction.AndroidActionRequested -> state.copy(
        pendingAndroidAction = action.action,
        draft = "",
        error = null,
    )
    is ChatAction.AndroidActionCompleted -> state.copy(
        pendingAndroidAction = null,
        messages = state.messages + ChatMessage(action.messageId, ChatRole.SYSTEM, action.description),
    )
    ChatAction.AndroidActionCancelled -> state.copy(pendingAndroidAction = null)
    ChatAction.Interrupted -> state.copy(busy = false)
    is ChatAction.Failed -> state.copy(loading = false, busy = false, error = action.message)
}

fun ChatUiState.streamState() = ChatStreamState(
    messages = messages,
    tools = tools,
    thinking = thinking,
    busy = busy,
    error = error,
)
