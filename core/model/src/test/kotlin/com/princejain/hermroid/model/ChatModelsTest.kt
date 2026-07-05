package com.princejain.hermroid.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelsTest {
    @Test
    fun `sessions sort current first then most recently active`() {
        val sessions = listOf(
            HermesSession(id = "old", title = "Old", lastActive = 10),
            HermesSession(id = "current", title = "Current", lastActive = 5, current = true),
            HermesSession(id = "new", title = "New", lastActive = 20),
        )

        assertEquals(listOf("current", "new", "old"), sessions.forDisplay().map { it.id })
    }

    @Test
    fun `wire transcript roles map to stable Android roles`() {
        assertEquals(ChatRole.USER, ChatRole.fromWire("user"))
        assertEquals(ChatRole.ASSISTANT, ChatRole.fromWire("assistant"))
        assertEquals(ChatRole.SYSTEM, ChatRole.fromWire("system"))
        assertEquals(ChatRole.TOOL, ChatRole.fromWire("tool"))
        assertEquals(ChatRole.SYSTEM, ChatRole.fromWire("future-role"))
    }

    @Test
    fun `model label removes provider path but keeps full id`() {
        val model = HermesModel(
            id = "openrouter/deepseek/deepseek-v4-flash",
            provider = "openrouter",
            current = true,
        )

        assertEquals("deepseek-v4-flash", model.label)
        assertEquals("openrouter/deepseek/deepseek-v4-flash", model.id)
    }
}
