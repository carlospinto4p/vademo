package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsTest {

    private val empty = Settings(token = "", authorName = "", authorEmail = "", repoUrls = Settings.DEFAULT_REPOS)

    @Test
    fun `the default settings can download the public example with no token`() {
        assertTrue(empty.canSync)
        assertFalse(empty.canWrite)
    }

    @Test
    fun `an empty repository list cannot sync`() {
        assertFalse(empty.copy(repoUrls = emptyList()).canSync)
    }

    @Test
    fun `writing needs the token, the name and the email`() {
        val full = empty.copy(token = "t", authorName = "n", authorEmail = "e@example.invalid")
        assertTrue(full.canWrite)
        assertFalse(full.copy(token = " ").canWrite)
        assertFalse(full.copy(authorName = "").canWrite)
        assertFalse(full.copy(authorEmail = "").canWrite)
    }
}
