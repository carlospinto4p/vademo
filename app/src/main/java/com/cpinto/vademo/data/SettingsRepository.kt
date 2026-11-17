package com.cpinto.vademo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpinto.vademo.kb.SavedQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val token: String,
    val authorName: String,
    val authorEmail: String,
    val repoUrls: List<String>,
    /**
     * A token is stored but this phone can no longer decrypt it, for example
     * after its Keystore key was reset. [token] is then empty.
     */
    val tokenUnreadable: Boolean = false,
) {
    /** Downloading a public knowledge base needs only an address to clone. */
    val canSync: Boolean
        get() = repoUrls.isNotEmpty()

    /** Private repositories, and sending edits back, also need a token and who to commit as. */
    val canWrite: Boolean
        get() = token.isNotBlank() && authorName.isNotBlank() && authorEmail.isNotBlank()

    companion object {
        /** The public example knowledge base. Replace or extend it in Settings. */
        val DEFAULT_REPOS = listOf(
            "https://github.com/carlospinto4p/kb-distributed-systems.git",
        )

        /** The folder an address is cloned into: `https://github.com/o/kb-x.git` → `kb-x`. */
        fun folderName(url: String): String = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git")

        /**
         * Addresses that would share one folder, grouped by it, in the order
         * given. Only the first of a group could ever sync, so Settings
         * refuses to save any.
         */
        fun sharedFolders(urls: List<String>): List<List<String>> =
            urls.groupBy(::folderName).values.filter { it.size > 1 }
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cipher = TokenCipher()
    private val token = DecryptOnce(cipher::decrypt)

    /** Emits only when a field of [Settings] changed, not on a text size or saved search. */
    val settings: Flow<Settings> = context.dataStore.data.map { settingsFrom(it, token::plain) }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    /**
     * How large note text is drawn, set by pinching a note. Kept apart from
     * [Settings] so that saving the settings form never resets a reading
     * preference the form does not show.
     */
    suspend fun textScale(): Float = context.dataStore.data.first()[TEXT_SCALE] ?: TextZoom.DEFAULT

    suspend fun saveTextScale(scale: Float) {
        context.dataStore.edit { prefs -> prefs[TEXT_SCALE] = TextZoom.clamp(scale) }
    }

    /** The searches saved from the search screen, in the order saved. */
    val savedQueries: Flow<List<SavedQuery>> = context.dataStore.data.map { SavedQueries.decode(it[SAVED_QUERIES]) }

    suspend fun saveQuery(name: String, query: String) {
        context.dataStore.edit { prefs ->
            prefs[SAVED_QUERIES] = SavedQueries.encode(SavedQueries.put(SavedQueries.decode(prefs[SAVED_QUERIES]), name, query))
        }
    }

    suspend fun deleteQuery(name: String) {
        context.dataStore.edit { prefs ->
            prefs[SAVED_QUERIES] = SavedQueries.encode(SavedQueries.remove(SavedQueries.decode(prefs[SAVED_QUERIES]), name))
        }
    }

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = if (settings.token.isBlank()) "" else cipher.encrypt(settings.token.trim())
            prefs[AUTHOR_NAME] = settings.authorName.trim()
            prefs[AUTHOR_EMAIL] = settings.authorEmail.trim()
            prefs[REPOS] = settings.repoUrls.joinToString("\n")
        }
    }

    /**
     * Forgets everything stored, as a fresh install has it. For device tests,
     * which share one settings store across a run.
     */
    internal suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    internal companion object {
        val TOKEN = stringPreferencesKey("github_token")
        val AUTHOR_NAME = stringPreferencesKey("author_name")
        val AUTHOR_EMAIL = stringPreferencesKey("author_email")
        val REPOS = stringPreferencesKey("repo_urls")
        val TEXT_SCALE = floatPreferencesKey("note_text_scale")
        val SAVED_QUERIES = stringPreferencesKey("saved_queries")
    }
}

/** Reads [Settings] from what is stored; [decrypt] returns `null` for a token it cannot read. */
internal fun settingsFrom(prefs: Preferences, decrypt: (String) -> String?): Settings {
    val stored = prefs[SettingsRepository.TOKEN]?.takeIf { it.isNotEmpty() }
    val token = stored?.let(decrypt)
    return Settings(
        token = token.orEmpty(),
        authorName = prefs[SettingsRepository.AUTHOR_NAME].orEmpty(),
        authorEmail = prefs[SettingsRepository.AUTHOR_EMAIL].orEmpty(),
        repoUrls = prefs[SettingsRepository.REPOS]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: Settings.DEFAULT_REPOS,
        tokenUnreadable = stored != null && token == null,
    )
}

/**
 * Remembers the last stored token and what it decrypted to, so the Keystore
 * is asked again only when the stored value changes. A value that could not
 * be decrypted is not remembered, so the next read tries again.
 */
internal class DecryptOnce(private val decrypt: (String) -> String?) {
    private var last: Pair<String, String>? = null

    @Synchronized
    fun plain(stored: String): String? {
        last?.let { (cipherText, plain) -> if (cipherText == stored) return plain }
        return decrypt(stored)?.also { last = stored to it }
    }
}
