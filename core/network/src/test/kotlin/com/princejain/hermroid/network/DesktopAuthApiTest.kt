package com.princejain.hermroid.network

import com.princejain.hermroid.model.ServerAddress
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DesktopAuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: DesktopAuthApi
    @Before fun start() { server=MockWebServer();server.start();api=DesktopAuthApi(ServerAddress.parse(server.url("/").toString())) }
    @After fun stop(){server.shutdown()}

    @Test fun decodesProviders()=runBlocking {
        server.enqueue(MockResponse().setBody("{\"providers\":[{\"name\":\"local\",\"display_name\":\"Local\",\"supports_password\":true}]}"))
        assertEquals("local",api.providers().single().name)
    }

    @Test fun loginCookieIsUsedForTicket()=runBlocking {
        server.enqueue(MockResponse().setHeader("Set-Cookie","hermes_session_at=test; Path=/").setBody("{\"ok\":true,\"next\":\"/\"}"))
        server.enqueue(MockResponse().setBody("{\"ticket\":\"once\",\"ttl_seconds\":30}"))
        assertTrue(api.login("local","user","pass").ok)
        assertEquals("once",api.ticket().ticket)
        val login=server.takeRequest(); assertEquals("/auth/password-login",login.path); assertTrue(login.body.readUtf8().contains("\"username\":\"user\""))
        assertTrue(server.takeRequest().getHeader("Cookie")!!.contains("hermes_session_at=test"))
    }
}
