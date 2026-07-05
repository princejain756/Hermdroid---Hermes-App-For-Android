package com.princejain.hermroid.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SecretKeysTest {
    @Test
    fun `secret keys are deterministic scoped and hide server names`() {
        val first = secretPreferenceKey("https://private.example.com", "password")
        val again = secretPreferenceKey("https://private.example.com", "password")
        val otherServer = secretPreferenceKey("https://other.example.com", "password")
        val otherName = secretPreferenceKey("https://private.example.com", "api-key")

        assertEquals(first, again)
        assertNotEquals(first, otherServer)
        assertNotEquals(first, otherName)
        assertFalse(first.contains("private.example.com"))
    }
}
