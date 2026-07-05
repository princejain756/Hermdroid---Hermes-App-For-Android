package com.princejain.hermroid.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCommandParserTest {
    private val parser = AndroidCommandParser()

    @Test
    fun `parses app navigation and accessibility commands`() {
        assertEquals(AndroidAction.OpenApp("WhatsApp"), parser.parse("open WhatsApp"))
        assertEquals(AndroidAction.Global(AndroidGlobalAction.BACK), parser.parse("go back"))
        assertEquals(AndroidAction.Global(AndroidGlobalAction.HOME), parser.parse("go home"))
        assertEquals(AndroidAction.TapText("Send"), parser.parse("tap Send"))
        assertEquals(AndroidAction.InputText("hello world"), parser.parse("type hello world"))
        assertEquals(AndroidAction.SetLauncherColumns(5), parser.parse("set home screen to 5 columns"))
    }

    @Test
    fun `parses WhatsApp and file compression commands`() {
        assertEquals(
            AndroidAction.WhatsAppMessage("+919876543210", "I am on my way"),
            parser.parse("message +91 98765 43210 on WhatsApp saying I am on my way"),
        )
        assertEquals(AndroidAction.CompressFiles, parser.parse("compress files"))
        assertNull(parser.parse("explain quantum computing"))
    }

    @Test
    fun `trusted mode bypasses confirmation without changing risk`() {
        val action = AndroidAction.TapText("Delete")

        assertTrue(ActionPolicy(trustedMode = false).requiresConfirmation(action))
        assertFalse(ActionPolicy(trustedMode = true).requiresConfirmation(action))
        assertEquals(ActionRisk.INTERACTIVE, action.risk)
    }
}
