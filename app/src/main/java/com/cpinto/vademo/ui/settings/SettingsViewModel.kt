package com.cpinto.vademo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.GitHubRepo
import com.cpinto.vademo.data.GitHubRepoFinder
import com.cpinto.vademo.data.KbAddress
import com.cpinto.vademo.data.RepoSearch
import com.cpinto.vademo.data.Settings
import com.cpinto.vademo.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The token's knowledge bases on GitHub, as far as Settings has asked. */
sealed interface GitHubList {
    /** Not asked yet for this token. */
    data object NotAsked : GitHubList
    data object Loading : GitHubList
    data class Shown(val repos: List<GitHubRepo>) : GitHubList
    data class Failed(val message: String) : GitHubList
}

data class SettingsForm(
    val loaded: Boolean = false,
    val token: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    /** The knowledge bases to sync, as clone addresses, in the order shown. */
    val repos: List<String> = emptyList(),
    /** What is typed in the add-by-address field. */
    val newAddress: String = "",
    /** Why that address cannot be added, shown under the field until it is edited. */
    val newAddressError: String? = null,
    val tokenUnreadable: Boolean = false,
    /** Why Save refused the knowledge-base list, shown under it until it changes. */
    val reposError: String? = null,
    val github: GitHubList = GitHubList.NotAsked,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val finder: GitHubRepoFinder,
) : ViewModel() {

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    private var search: Job? = null

    fun load() {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val current = repository.current()
            _form.value = SettingsForm(
                loaded = true,
                token = current.token,
                authorName = current.authorName,
                authorEmail = current.authorEmail,
                repos = current.repoUrls,
                tokenUnreadable = current.tokenUnreadable,
            )
            if (current.token.isNotBlank()) findOnGitHub()
        }
    }

    fun update(change: (SettingsForm) -> SettingsForm) = _form.update(change)

    /** A new token has not been asked about: the list shown was for the old one. */
    fun updateToken(token: String) {
        search?.cancel()
        _form.update { it.copy(token = token, github = GitHubList.NotAsked) }
    }

    fun updateNewAddress(text: String) = _form.update { it.copy(newAddress = text, newAddressError = null) }

    /** Lists the token's `kb-` repositories, public and private. */
    fun findOnGitHub() {
        val token = _form.value.token
        search?.cancel()
        _form.update { it.copy(github = GitHubList.Loading) }
        search = viewModelScope.launch {
            val found = when (val result = finder.find(token)) {
                is RepoSearch.Found -> GitHubList.Shown(result.repos)
                is RepoSearch.Failed -> GitHubList.Failed(result.message)
            }
            _form.update { it.copy(github = found) }
        }
    }

    /** Adds what is typed in the address field; `false`, with the reason shown, when it cannot be. */
    fun addTyped(): Boolean {
        val form = _form.value
        val problem = when (val parsed = KbAddress.parse(form.newAddress)) {
            is KbAddress.Parsed.Invalid -> parsed.reason
            is KbAddress.Parsed.Valid -> KbAddress.conflict(form.repos, parsed.address) ?: run {
                add(parsed.address.url)
                _form.update { it.copy(newAddress = "") }
                return true
            }
        }
        _form.update { it.copy(newAddressError = problem) }
        return false
    }

    fun addFound(repo: GitHubRepo) {
        if (KbAddress.conflict(_form.value.repos, KbAddress.of(repo.cloneUrl)) == null) add(repo.cloneUrl)
    }

    fun remove(url: String) = _form.update { form -> form.copy(repos = form.repos - url, reposError = null) }

    private fun add(url: String) = _form.update { it.copy(repos = it.repos + url, reposError = null) }

    fun save(onSaved: () -> Unit) {
        // An address typed but not added yet is meant to be in the list.
        if (_form.value.newAddress.isNotBlank() && !addTyped()) return
        val form = _form.value
        val shared = Settings.sharedFolders(form.repos).firstOrNull()
        if (shared != null) {
            _form.update {
                it.copy(
                    reposError = "These end in the same name, ${Settings.folderName(shared.first())}, " +
                        "and only one can be synced: ${shared.joinToString(", ")}. Keep one.",
                )
            }
            return
        }
        viewModelScope.launch {
            repository.save(
                Settings(
                    token = form.token,
                    authorName = form.authorName,
                    authorEmail = form.authorEmail,
                    repoUrls = form.repos,
                )
            )
            onSaved()
        }
    }
}
