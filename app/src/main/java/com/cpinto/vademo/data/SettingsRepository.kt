package com.cpinto.vademo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Settings(
    val token: String,
    val authorName: String,
    val authorEmail: String,
    val repoUrls: List<String>,
) {
    val isComplete: Boolean
        get() = token.isNotBlank() && authorName.isNotBlank() && authorEmail.isNotBlank() && repoUrls.isNotEmpty()

    companion object {
        /** The knowledge bases as of 2026-09-20, all private projects on GitHub. Editable in Settings. */
        val DEFAULT_REPOS = listOf(
            "https://github.com/carlospinto4p/kb-business.git",
            "https://github.com/carlospinto4p/kb-youtube-channel.git",
            "https://github.com/carlospinto4p/kb-investments.git",
            "https://github.com/carlospinto4p/kb-videogames-market.git",
        )
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cipher = TokenCipher()

    val settings: Flow<Settings> = context.dataStore.data.map(::toSettings)

    suspend fun current(): Settings = settings.first()

    suspend fun save(settings: Settings) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN] = if (settings.token.isBlank()) "" else cipher.encrypt(settings.token.trim())
            prefs[AUTHOR_NAME] = settings.authorName.trim()
            prefs[AUTHOR_EMAIL] = settings.authorEmail.trim()
            prefs[REPOS] = settings.repoUrls.joinToString("\n")
        }
    }

    private fun toSettings(prefs: Preferences) = Settings(
        token = prefs[TOKEN]?.takeIf { it.isNotEmpty() }?.let(cipher::decrypt).orEmpty(),
        authorName = prefs[AUTHOR_NAME].orEmpty(),
        authorEmail = prefs[AUTHOR_EMAIL].orEmpty(),
        repoUrls = prefs[REPOS]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: Settings.DEFAULT_REPOS,
    )

    private companion object {
        val TOKEN = stringPreferencesKey("github_token")
        val AUTHOR_NAME = stringPreferencesKey("author_name")
        val AUTHOR_EMAIL = stringPreferencesKey("author_email")
        val REPOS = stringPreferencesKey("repo_urls")
    }
}
