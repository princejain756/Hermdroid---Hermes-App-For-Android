package com.princejain.hermroid.chat

import com.princejain.hermroid.model.ChatMessage
import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.HermesModel
import com.princejain.hermroid.model.HermesSession
import com.princejain.hermroid.automation.AndroidAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStateReducerTest {
    @Test
    fun `opening session replaces transcript and selects current model`() {
        val state = reduceChatState(
            ChatUiState(),
            ChatAction.SessionOpened(
                sessionId = "sid-1",
                messages = listOf(ChatMessage("m1", ChatRole.USER, "Hello")),
                models = listOf(
                    HermesModel("provider/model-a", "provider"),
                    HermesModel("provider/model-b", "provider", current = true),
                ),
            ),
        )

        assertEquals("sid-1", state.currentSessionId)
        assertEquals("Hello", state.messages.single().text)
        assertEquals("provider/model-b", state.selectedModelId)
        assertFalse(state.loading)
    }

    @Test
    fun `sending appends user message clears composer and marks busy`() {
        val initial = ChatUiState(currentSessionId = "sid-1", draft = "Build it")

        val state = reduceChatState(initial, ChatAction.SendStarted(messageId = "local-1"))

        assertEquals("", state.draft)
        assertEquals("Build it", state.messages.single().text)
        assertEquals(ChatRole.USER, state.messages.single().role)
        assertTrue(state.busy)
    }

    @Test
    fun `session model and interrupt actions update only their state`() {
        val sessions = listOf(HermesSession("sid-2", "Second"), HermesSession("sid-1", "First", current = true))
        var state = reduceChatState(ChatUiState(), ChatAction.SessionsLoaded(sessions))
        state = reduceChatState(state, ChatAction.ModelSelected("provider/model-c"))
        state = reduceChatState(state.copy(busy = true), ChatAction.Interrupted)

        assertEquals(listOf("sid-1", "sid-2"), state.sessions.map { it.id })
        assertEquals("provider/model-c", state.selectedModelId)
        assertFalse(state.busy)
    }

    @Test
    fun `agent requests remain visible until answered`() {
        var state = reduceChatState(
            ChatUiState(),
            ChatAction.ApprovalRequested(
                ApprovalRequest("rm -rf build", "Delete generated files", allowPermanent = false),
            ),
        )
        assertEquals("rm -rf build", state.approval?.command)

        state = reduceChatState(state, ChatAction.RequestAnswered)
        assertEquals(null, state.approval)

        state = reduceChatState(
            state,
            ChatAction.ClarificationRequested(
                ClarificationRequest("request-1", "Which platform?", listOf("Android", "iOS")),
            ),
        )
        assertEquals(listOf("Android", "iOS"), state.clarification?.choices)
    }

    @Test
    fun `Android action waits for confirmation and records result`() {
        var state = reduceChatState(
            ChatUiState(draft = "open WhatsApp"),
            ChatAction.AndroidActionRequested(AndroidAction.OpenApp("WhatsApp")),
        )
        assertEquals(AndroidAction.OpenApp("WhatsApp"), state.pendingAndroidAction)
        assertEquals("", state.draft)

        state = reduceChatState(state, ChatAction.AndroidActionCompleted("Opened WhatsApp", "action-1"))
        assertEquals(null, state.pendingAndroidAction)
        assertEquals("Opened WhatsApp", state.messages.single().text)
        assertEquals(ChatRole.SYSTEM, state.messages.single().role)
    }
}
