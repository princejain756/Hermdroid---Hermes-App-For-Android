package com.princejain.hermroid.network

import com.princejain.hermroid.model.ChatRole
import com.princejain.hermroid.model.ChatStreamState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopChatReducerTest {
    private val reducer = DesktopChatReducer("sid-1")

    @Test
    fun `message events build one streaming assistant response`() {
        var state = ChatStreamState()

        state = reducer.reduce(state, event("message.start"))
        state = reducer.reduce(state, event("message.delta", mapOf("text" to "Hello ")))
        state = reducer.reduce(state, event("message.delta", mapOf("text" to "Android")))
        state = reducer.reduce(
            state,
            event("message.complete", mapOf("rendered" to "Hello Android", "reasoning" to "Done")),
        )

        assertEquals(1, state.messages.size)
        assertEquals(ChatRole.ASSISTANT, state.messages.single().role)
        assertEquals("Hello Android", state.messages.single().text)
        assertEquals("Done", state.messages.single().reasoning)
        assertFalse(state.messages.single().streaming)
        assertFalse(state.busy)
    }

    @Test
    fun `thinking and tools remain visible as structured activity`() {
        var state = ChatStreamState()

        state = reducer.reduce(state, event("thinking.delta", mapOf("text" to "Inspecting")))
        state = reducer.reduce(
            state,
            event("tool.start", mapOf("tool_id" to "tool-1", "name" to "terminal", "args_text" to "ls")),
        )
        state = reducer.reduce(
            state,
            event("tool.complete", mapOf("tool_id" to "tool-1", "summary" to "Listed files")),
        )

        assertEquals("Inspecting", state.thinking)
        assertEquals("terminal", state.tools.single().name)
        assertEquals("Listed files", state.tools.single().summary)
        assertFalse(state.tools.single().running)
        assertNull(state.tools.single().error)
    }

    @Test
    fun `foreign session events are ignored and errors end busy state`() {
        val initial = ChatStreamState(busy = true)

        val ignored = reducer.reduce(
            initial,
            DesktopEvent(type = "message.delta", payload = mapOf("text" to "Wrong"), sessionId = "sid-2"),
        )
        val failed = reducer.reduce(
            ignored,
            event("error", mapOf("message" to "Provider unavailable")),
        )

        assertEquals(initial, ignored)
        assertEquals("Provider unavailable", failed.error)
        assertFalse(failed.busy)
        assertTrue(failed.messages.isEmpty())
    }

    private fun event(type: String, payload: Map<String, Any?> = emptyMap()) =
        DesktopEvent(type = type, payload = payload, sessionId = "sid-1")
}
