package com.cpinto.vademo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cpinto.vademo.data.Settings
import com.cpinto.vademo.data.SettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Settings saved and read back on a device, where the token goes through the
 * Android Keystore. The JVM tests stand in for the Keystore, so only here is
 * the real encryption checked.
 */
@RunWith(AndroidJUnit4::class)
class SettingsStorageTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = SettingsRepository(context)

    @Before
    @After
    fun clear() = runBlocking { repository.clear() }

    @Test
    fun theTokenIsReadBackButNotStoredAsTyped() = runBlocking {
        repository.save(Settings("ghp_example_token", "Test", "test@example.invalid", listOf("https://example.invalid/kb-x.git")))
        assertEquals("ghp_example_token", repository.current().token)
        assertFalse(repository.current().tokenUnreadable)
        val stored = File(context.filesDir, "datastore/settings.preferences_pb").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(stored, "ghp_example_token" in stored)
    }

    @Test
    fun savingTrimsTheTokenTheNameAndTheEmail() = runBlocking {
        repository.save(Settings(" ghp_example_token\n", "  Test ", " test@example.invalid ", listOf(" https://example.invalid/kb-x.git ")))
        val read = repository.current()
        assertEquals("ghp_example_token", read.token)
        assertEquals("Test", read.authorName)
        assertEquals("test@example.invalid", read.authorEmail)
        assertEquals(listOf("https://example.invalid/kb-x.git"), read.repoUrls)
    }

    @Test
    fun aBlankTokenIsStoredAsNone() = runBlocking {
        repository.save(Settings("  ", "Test", "test@example.invalid", Settings.DEFAULT_REPOS))
        val read = repository.current()
        assertEquals("", read.token)
        assertFalse(read.tokenUnreadable)
    }

    @Test
    fun nothingSavedMeansTheExampleAndAnEmptyListStaysEmpty() = runBlocking {
        assertEquals(Settings.DEFAULT_REPOS, repository.current().repoUrls)
        repository.save(Settings("", "", "", emptyList()))
        assertEquals(emptyList<String>(), repository.current().repoUrls)
    }
}
