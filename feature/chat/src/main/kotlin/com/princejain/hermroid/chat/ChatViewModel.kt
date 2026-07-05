package com.princejain.hermroid.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princejain.hermroid.automation.ActionPolicy
import com.princejain.hermroid.automation.ActionResult
import com.princejain.hermroid.automation.AndroidAction
import com.princejain.hermroid.automation.AndroidCommandParser
import com.princejain.hermroid.network.DesktopChatReducer
import com.princejain.hermroid.network.DesktopHermesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(
    private val api: DesktopHermesApi,
    private val executeAndroidAction: (AndroidAction) -> ActionResult,
    private val trustedMode: () -> Boolean,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state = mutableState.asStateFlow()
    private val localIds = AtomicLong()
    private val commandParser = AndroidCommandParser()

    init {
        viewModelScope.launch {
            api.events.collect { event ->
                val sessionId = mutableState.value.currentSessionId ?: return@collect
                when (event.type) {
                    "approval.request" -> dispatch(
                        ChatAction.ApprovalRequested(
                            ApprovalRequest(
                                command = event.payload["command"] as? String ?: "",
                                description = event.payload["description"] as? String ?: "Dangerous command",
                                allowPermanent = event.payload["allow_permanent"] as? Boolean ?: true,
                            ),
                        ),
                    )
                    "clarify.request" -> dispatch(
                        ChatAction.ClarificationRequested(
                            ClarificationRequest(
                                requestId = event.payload["request_id"] as? String ?: return@collect,
                                question = event.payload["question"] as? String ?: "Hermes needs more information",
                                choices = (event.payload["choices"] as? List<*>)?.mapNotNull { it as? String }.orEmpty(),
                            ),
                        ),
                    )
                    else -> {
                        val stream = DesktopChatReducer(sessionId).reduce(mutableState.value.streamState(), event)
                        dispatch(ChatAction.StreamUpdated(stream))
                    }
                }
            }
        }
        refreshSessions()
    }

    fun refreshSessions() = launchAction {
        val sessions = api.listSessions()
        dispatch(ChatAction.SessionsLoaded(sessions))
        val target = sessions.maxByOrNull { it.startedAt }?.id ?: api.createSession()
        openSession(target)
    }

    fun newSession() = launchAction {
        openSession(api.createSession())
        dispatch(ChatAction.SessionsLoaded(api.listSessions()))
    }

    fun openSession(sessionId: String) = launchAction {
        val snapshot = api.resumeSession(sessionId)
        val models = api.models(sessionId)
        dispatch(
            ChatAction.SessionOpened(
                sessionId = snapshot.sessionId,
                messages = snapshot.messages,
                models = models,
                running = snapshot.running,
            ),
        )
    }

    fun draft(value: String) = dispatch(ChatAction.DraftChanged(value))

    fun send() {
        val sessionId = mutableState.value.currentSessionId ?: return
        val text = mutableState.value.draft.trim()
        if (text.isEmpty() || mutableState.value.busy) return
        commandParser.parse(text)?.let { action ->
            dispatch(ChatAction.AndroidActionRequested(action))
            if (!ActionPolicy(trustedMode()).requiresConfirmation(action)) executeAndroid(action)
            return
        }
        dispatch(ChatAction.SendStarted("local-${localIds.incrementAndGet()}"))
        launchAction { check(api.submit(sessionId, text)) { "Hermes did not accept the message" } }
    }

    fun selectModel(modelId: String) {
        val sessionId = mutableState.value.currentSessionId ?: return
        launchAction {
            val selected = api.selectModel(sessionId, modelId)
            dispatch(ChatAction.ModelSelected(selected))
        }
    }

    fun interrupt() {
        val sessionId = mutableState.value.currentSessionId ?: return
        launchAction {
            api.interrupt(sessionId)
            dispatch(ChatAction.Interrupted)
        }
    }

    fun answerApproval(choice: String) {
        val sessionId = mutableState.value.currentSessionId ?: return
        launchAction {
            check(api.respondToApproval(sessionId, choice)) { "Hermes did not accept the approval response" }
            dispatch(ChatAction.RequestAnswered)
        }
    }

    fun answerClarification(answer: String) {
        val requestId = mutableState.value.clarification?.requestId ?: return
        launchAction {
            check(api.respondToClarification(requestId, answer)) { "Hermes did not accept the answer" }
            dispatch(ChatAction.RequestAnswered)
        }
    }

    fun approveAndroidAction() {
        mutableState.value.pendingAndroidAction?.let(::executeAndroid)
    }

    fun cancelAndroidAction() = dispatch(ChatAction.AndroidActionCancelled)

    private fun executeAndroid(action: AndroidAction) {
        val description = when (val result = executeAndroidAction(action)) {
            is ActionResult.Completed -> result.description
            is ActionResult.Failed -> result.message
            ActionResult.RequiresAccessibilityPermission -> "Enable Hermroid in Android Accessibility settings, then try again."
            ActionResult.RequiresFilePicker -> "Choose the files to compress."
        }
        dispatch(ChatAction.AndroidActionCompleted(description, "action-${localIds.incrementAndGet()}"))
    }

    private fun dispatch(action: ChatAction) {
        mutableState.value = reduceChatState(mutableState.value, action)
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                dispatch(ChatAction.Failed(error.message ?: "Hermes request failed"))
            }
        }
    }

    companion object {
        fun factory(
            api: DesktopHermesApi,
            executeAndroidAction: (AndroidAction) -> ActionResult,
            trustedMode: () -> Boolean,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(api, executeAndroidAction, trustedMode) as T
        }
    }
}
