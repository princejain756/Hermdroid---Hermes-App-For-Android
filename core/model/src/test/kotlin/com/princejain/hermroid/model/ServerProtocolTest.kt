package com.princejain.hermroid.model
import org.junit.Assert.*
import org.junit.Test
class ServerProtocolTest { @Test fun autoIsNotResolved(){ assertFalse(ServerProtocol.AUTO.isResolved); assertTrue(ServerProtocol.DESKTOP.isResolved); assertTrue(ServerProtocol.WEB_UI.isResolved) } }
