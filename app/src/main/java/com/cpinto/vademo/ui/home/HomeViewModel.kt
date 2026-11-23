package com.cpinto.vademo.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbInfo
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.data.SyncState
import com.cpinto.vademo.kb.diagramsIn
import com.cpinto.vademo.kb.mainDiagram
import com.cpinto.vademo.ui.kb.KbStart
import com.cpinto.vademo.ui.kb.KbTab
import com.cpinto.vademo.ui.kb.firstTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

data class HomeState(
    val kbs: List<KbInfo> = emptyList(),
    val syncStates: Map<String, SyncState> = emptyMap(),
    val hasRepos: Boolean = true,
    val canWrite: Boolean = true,
    val tokenUnreadable: Boolean = false,
) {
    val syncing: Boolean
        get() = syncStates.values.any { it == SyncState.Running }

    val canSync: Boolean
        get() = hasRepos && !syncing
}

/**
 * Of [kbs], the one named [last] when it can be opened as it is: downloaded,
 * readable and not waiting for a choice after a conflict. Anything else
 * needs the home screen.
 */
internal fun kbToReopen(last: String?, kbs: List<KbInfo>): String? =
    kbs.firstOrNull { it.name == last }
        ?.takeIf { it.cloned && !it.needsChoice && it.problem == null }
        ?.name

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: KbRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val kbs = MutableStateFlow<List<KbInfo>>(emptyList())

    val state: StateFlow<HomeState> =
        combine(kbs, repository.syncStates, settings.settings) { kbs, syncStates, current ->
            HomeState(kbs, syncStates, current.canSync, current.canWrite, current.tokenUnreadable)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /**
     * Lists the knowledge bases. The first time after launch it also reopens,
     * with [onOpen], the one the app was left in; any later time the home
     * screen shows, it was come back to, so the next launch starts here.
     */
    fun start(onOpen: (KbStart) -> Unit) {
        viewModelScope.launch {
            val list = repository.list()
            kbs.value = list
            // Saved, so a home screen rebuilt after the process was stopped does not count as a launch.
            if (savedState.get<Boolean>(LAUNCHED) == true) {
                settings.saveLastKb(null)
                return@launch
            }
            savedState[LAUNCHED] = true
            kbToReopen(settings.lastKb(), list)?.let { onOpen(startOf(it)) }
        }
    }

    /** Opens the knowledge base [name] with [onOpen], once it is known which tab and diagram it starts on. */
    fun open(name: String, onOpen: (KbStart) -> Unit) {
        viewModelScope.launch { onOpen(startOf(name)) }
    }

    /** Reads the index and the diagrams, so a knowledge base with a main diagram opens on it. */
    private suspend fun startOf(name: String): KbStart {
        val diagram = withContext(Dispatchers.IO) {
            val kb = repository.knowledgeBase(name)
            val index = try {
                kb.index?.readText()
            } catch (e: IOException) {
                null
            }
            mainDiagram(index, diagramsIn(kb.root).filter { it.diagram != null }.map { it.name })
        }
        return KbStart(name, firstTab(KbTab.parse(settings.kbTab(name)), diagram), diagram)
    }

    fun syncAll() {
        viewModelScope.launch {
            repository.syncAll()
            kbs.value = repository.list()
        }
    }

    private companion object {
        const val LAUNCHED = "launched"
    }
}
