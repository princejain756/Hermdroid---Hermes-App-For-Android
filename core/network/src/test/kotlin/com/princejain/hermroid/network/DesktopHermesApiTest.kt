package com.princejain.hermroid.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopHermesApiTest {
    @Test
    fun `session lifecycle uses official RPC methods and maps transcript`() = runBlocking {
        val rpc = FakeRpc(
            "session.list" to mapOf(
                "sessions" to listOf(
                    mapOf(
                        "id" to "sid-1",
                        "title" to "Android work",
                        "preview" to "Build the app",
                        "message_count" to 4.0,
                        "started_at" to 100.0,
                    ),
                ),
            ),
            "session.create" to mapOf("session_id" to "sid-new"),
            "session.resume" to mapOf(
                "session_id" to "sid-1",
                "running" to false,
                "messages" to listOf(
                    mapOf("role" to "user", "text" to "Hello"),
                    mapOf("role" to "assistant", "text" to "Hi"),
                ),
            ),
        )
        val api = DesktopHermesApi(rpc)

        val sessions = api.listSessions()
        val created = api.createSession(columns = 96)
        val resumed = api.resumeSession("sid-1", columns = 96)

        assertEquals("Android work", sessions.single().title)
        assertEquals(4, sessions.single().messageCount)
        assertEquals("sid-new", created)
        assertFalse(resumed.running)
        assertEquals(listOf("Hello", "Hi"), resumed.messages.map { it.text })
        assertEquals(
            listOf(
                Call("session.list", emptyMap()),
                Call("session.create", mapOf("cols" to 96)),
                Call("session.resume", mapOf("session_id" to "sid-1", "cols" to 96)),
            ),
            rpc.calls,
        )
    }

    @Test
    fun `model switch prompt submit and interrupt use official RPC methods`() = runBlocking {
        val rpc = FakeRpc(
            "model.options" to mapOf(
                "model" to "openrouter/deepseek-v4-flash",
                "providers" to listOf(
                    mapOf(
                        "slug" to "openrouter",
                        "name" to "OpenRouter",
                        "authenticated" to true,
                        "models" to listOf("openrouter/deepseek-v4-flash", "openrouter/gpt-5.5"),
                    ),
                ),
            ),
            "config.set" to mapOf("value" to "openrouter/gpt-5.5"),
            "prompt.submit" to mapOf("ok" to true),
            "session.interrupt" to mapOf("ok" to true),
        )
        val api = DesktopHermesApi(rpc)

        val models = api.models("sid-1")
        val selected = api.selectModel("sid-1", "openrouter/gpt-5.5")
        val submitted = api.submit("sid-1", "Build Hermroid")
        val interrupted = api.interrupt("sid-1")

        assertEquals(2, models.size)
        assertTrue(models.first().current)
        assertEquals("openrouter/gpt-5.5", selected)
        assertTrue(submitted)
        assertTrue(interrupted)
        assertEquals(
            listOf(
                Call("model.options", mapOf("session_id" to "sid-1")),
                Call("config.set", mapOf("key" to "model", "session_id" to "sid-1", "value" to "openrouter/gpt-5.5")),
                Call("prompt.submit", mapOf("session_id" to "sid-1", "text" to "Build Hermroid")),
                Call("session.interrupt", mapOf("session_id" to "sid-1")),
            ),
            rpc.calls,
        )
    }

    @Test
    fun `approval and clarification responses use official RPC methods`() = runBlocking {
        val rpc = FakeRpc(
            "approval.respond" to mapOf("ok" to true),
            "clarify.respond" to mapOf("ok" to true),
        )
        val api = DesktopHermesApi(rpc)

        assertTrue(api.respondToApproval("sid-1", "once"))
        assertTrue(api.respondToClarification("request-1", "Android"))
        assertEquals(
            listOf(
                Call("approval.respond", mapOf("choice" to "once", "session_id" to "sid-1")),
                Call("clarify.respond", mapOf("answer" to "Android", "request_id" to "request-1")),
            ),
            rpc.calls,
        )
    }
}

private data class Call(val method: String, val params: Map<String, Any?>)

private class FakeRpc(vararg responses: Pair<String, Any?>) : JsonRpcTransport {
    private val responses = responses.toMap()
    override val events = MutableSharedFlow<DesktopEvent>()
    val calls = mutableListOf<Call>()

    override suspend fun request(method: String, params: Map<String, Any?>): Any? {
        calls += Call(method, params)
        return responses[method]
    }

    override fun disconnect() = Unit
}
