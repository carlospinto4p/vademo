package com.cpinto.vademo.data

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `addresses ending in the same name are found, with or without git and a slash`() {
        val urls = listOf(
            "https://github.com/a/kb-x.git",
            "https://github.com/c/kb-y",
            "https://github.com/b/kb-x/",
        )
        assertEquals(listOf(listOf(urls[0], urls[2])), Settings.sharedFolders(urls))
        assertEquals("kb-x", Settings.folderName(urls[2]))
    }

    @Test
    fun `addresses with different names share nothing`() {
        assertEquals(emptyList<List<String>>(), Settings.sharedFolders(Settings.DEFAULT_REPOS + "https://github.com/a/kb-x"))
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
