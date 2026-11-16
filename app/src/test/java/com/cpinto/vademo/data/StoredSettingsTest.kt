package com.cpinto.vademo.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StoredSettingsTest {

    private fun decrypt(stored: String): String? = stored.removePrefix("sealed:").takeIf { stored.startsWith("sealed:") }

    @Test
    fun `a stored token is read back`() {
        val settings = settingsFrom(mutablePreferencesOf(SettingsRepository.TOKEN to "sealed:ghp_abc"), ::decrypt)
        assertEquals("ghp_abc", settings.token)
        assertFalse(settings.tokenUnreadable)
    }

    @Test
    fun `a stored token that cannot be decrypted is reported, not just emptied`() {
        val settings = settingsFrom(mutablePreferencesOf(SettingsRepository.TOKEN to "lost-key"), ::decrypt)
        assertEquals("", settings.token)
        assertTrue(settings.tokenUnreadable)
    }

    @Test
    fun `no stored token is not an unreadable one`() {
        assertFalse(settingsFrom(mutablePreferencesOf(), ::decrypt).tokenUnreadable)
        assertFalse(settingsFrom(mutablePreferencesOf(SettingsRepository.TOKEN to ""), ::decrypt).tokenUnreadable)
    }
}
