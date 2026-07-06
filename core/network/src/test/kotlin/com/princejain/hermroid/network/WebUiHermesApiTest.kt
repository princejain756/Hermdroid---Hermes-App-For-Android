package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WebUiHermesApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: WebUiHermesApi

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        val address = ServerAddress.parse(server.url("/").toString())
        api = WebUiHermesApi(address)
    }

    @After fun tearDown() {
        api.disconnect()
        server.shutdown()
    }

    @Test fun `session lifecycle maps webui responses`() = runBlocking {
        server.enqueue(json("""{"sessions":[{"session_id":"s1","title":"Android work","message_count":2,"created_at":100,"updated_at":120}]}"""))
        server.enqueue(json("""{"session":{"session_id":"new","title":"New chat","messages":[],"model":"m1","model_provider":"openrouter"}}"""))
        server.enqueue(json("""{"session":{"session_id":"s1","title":"Android work","model":"m1","model_provider":"openrouter","active_stream_id":null,"messages":[{"role":"user","content":"Hello"},{"role":"assistant","content":"Hi"}]}}"""))

        val sessions = api.listSessions()
        val created = api.createSession()
        val resumed = api.resumeSession("s1")

        assertEquals("Android work", sessions.single().title)
        assertEquals("new", created)
        assertEquals(listOf("Hello", "Hi"), resumed.messages.map { it.text })
        assertFalse(resumed.running)
        assertEquals("/api/sessions", server.takeRequest().path)
        assertEquals("/api/session/new", server.takeRequest().path)
        assertEquals("/api/session?session_id=s1&messages=1&resolve_model=0", server.takeRequest().path)
    }

    @Test fun `models and selection preserve provider`() = runBlocking {
        server.enqueue(json("""{"groups":[{"provider":"OpenRouter","provider_id":"openrouter","models":[{"id":"m1","label":"One"},{"id":"m2","label":"Two"}]}]}"""))
        server.enqueue(json("""{"session":{"session_id":"s1","model":"m2","model_provider":"openrouter","messages":[]}}"""))

        val models = api.models("s1")
        val selected = api.selectModel("s1", "m2")

        assertEquals(listOf("m1", "m2"), models.map { it.id })
        assertEquals("openrouter", models.single { it.id == "m2" }.provider)
        assertEquals("m2", selected)
        assertTrue(server.takeRequest().path!!.startsWith("/api/models"))
        val update = server.takeRequest()
        assertEquals("/api/session/update", update.path)
        assertTrue(update.body.readUtf8().contains("\"model_provider\":\"openrouter\""))
    }

    @Test fun `submit streams canonical chat events and can cancel`() = runBlocking {
        api.seedSessionForTest("s1", model = "m1", provider = "openrouter", workspace = "/work")
        server.enqueue(json("""{"stream_id":"stream-1"}"""))
        server.enqueue(
            MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                "event: token\ndata: {\"text\":\"Hel\"}\n\n" +
                    "event: reasoning\ndata: {\"text\":\"Think\"}\n\n" +
                    "event: token\ndata: {\"text\":\"lo\"}\n\n" +
                    "event: done\ndata: {\"session\":{\"session_id\":\"s1\",\"messages\":[{\"role\":\"assistant\",\"content\":\"Hello\"}]}}\n\n" +
                    "event: stream_end\ndata: {\"session_id\":\"s1\"}\n\n",
            ),
        )
        server.enqueue(json("""{"ok":true,"cancelled":true,"stream_id":"stream-1"}"""))

        val events = mutableListOf<DesktopEvent>()
        val collector = async {
            api.events.take(4).toList(events)
        }
        assertTrue(api.submit("s1", "Hello"))
        collector.await()
        assertEquals(listOf("message.delta", "reasoning.delta", "message.delta", "message.complete"), events.map { it.type })
        assertEquals("Hello", events.last().payload["text"])
        assertTrue(api.interrupt("s1"))

        val start = server.takeRequest()
        assertEquals("/api/chat/start", start.path)
        assertTrue(start.body.readUtf8().contains("\"workspace\":\"/work\""))
        assertEquals("/api/chat/stream?stream_id=stream-1", server.takeRequest().path)
        assertEquals("/api/chat/cancel?stream_id=stream-1", server.takeRequest().path)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
