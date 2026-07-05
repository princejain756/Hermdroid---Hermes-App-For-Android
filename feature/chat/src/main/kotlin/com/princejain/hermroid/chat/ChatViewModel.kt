package com.princejain.hermroid.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princejain.hermroid.network.DesktopChatReducer
import com.princejain.hermroid.network.DesktopHermesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class ChatViewModel(private val api: DesktopHermesApi) : ViewModel() {
    private val mutableState = MutableStateFlow(ChatUiState())
    val state = mutableState.asStateFlow()
    private val localIds = AtomicLong()

    init {
        viewModelScope.launch {
            api.events.collect { event ->
                val sessionId = mutableState.value.currentSessionId ?: return@collect
                val stream = DesktopChatReducer(sessionId).reduce(mutableState.value.streamState(), event)
                dispatch(ChatAction.StreamUpdated(stream))
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
        fun factory(api: DesktopHermesApi) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(api) as T
        }
    }
}
