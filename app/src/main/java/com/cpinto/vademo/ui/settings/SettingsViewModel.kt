package com.cpinto.vademo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.Settings
import com.cpinto.vademo.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsForm(
    val loaded: Boolean = false,
    val token: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    val repos: String = "",
    val tokenUnreadable: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    fun load() {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val current = repository.current()
            _form.value = SettingsForm(
                loaded = true,
                token = current.token,
                authorName = current.authorName,
                authorEmail = current.authorEmail,
                repos = current.repoUrls.joinToString("\n"),
                tokenUnreadable = current.tokenUnreadable,
            )
        }
    }

    fun update(change: (SettingsForm) -> SettingsForm) = _form.update(change)

    fun save(onSaved: () -> Unit) {
        val form = _form.value
        viewModelScope.launch {
            repository.save(
                Settings(
                    token = form.token,
                    authorName = form.authorName,
                    authorEmail = form.authorEmail,
                    repoUrls = form.repos.lines().map { it.trim() }.filter { it.isNotEmpty() },
                )
            )
            onSaved()
        }
    }
}
