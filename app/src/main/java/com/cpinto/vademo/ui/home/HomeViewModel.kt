package com.cpinto.vademo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbInfo
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.data.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val kbs: List<KbInfo> = emptyList(),
    val syncStates: Map<String, SyncState> = emptyMap(),
    val hasRepos: Boolean = true,
    val canWrite: Boolean = true,
) {
    val canSync: Boolean
        get() = hasRepos && syncStates.values.none { it == SyncState.Running }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: KbRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val kbs = MutableStateFlow<List<KbInfo>>(emptyList())

    val state: StateFlow<HomeState> =
        combine(kbs, repository.syncStates, settings.settings) { kbs, syncStates, current ->
            HomeState(kbs, syncStates, current.canSync, current.canWrite)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun refresh() {
        viewModelScope.launch { kbs.value = repository.list() }
    }

    fun syncAll() {
        viewModelScope.launch {
            repository.syncAll()
            kbs.value = repository.list()
        }
    }
}
