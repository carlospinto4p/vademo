package com.cpinto.vademo.ui.kb

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.kb.IndexedNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A knowledge base's first screen: which of its tabs shows. The tab is kept
 * in [SavedStateHandle], so it survives the app being stopped, and in the
 * settings, so the knowledge base opens on it next time.
 */
@HiltViewModel
class KbViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val settings: SettingsRepository,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])

    /** The main diagram's name, shown on the Diagrams tab; empty when there is none. */
    val diagram: String = savedState["name"] ?: ""

    /** The name of the tab showing, the route's `tab` until another is chosen. */
    val tab: StateFlow<String> = savedState.getStateFlow(TAB, KbTab.Map.name)

    /** The notes last opened here, newest first, that still exist; for the "Continue reading" strip. */
    private val _recent = MutableStateFlow<List<IndexedNote>>(emptyList())
    val recent: StateFlow<List<IndexedNote>> = _recent

    /** Reads [recent] again, as when coming back from a note. */
    fun loadRecent() {
        viewModelScope.launch {
            val paths = settings.recentNotes(kbName)
            if (paths.isEmpty()) return@launch
            val index = repository.index(kbName)
            _recent.value = paths.mapNotNull(index::noteAt)
        }
    }

    init {
        // Reopened at the next launch, unless the home screen is gone back to first.
        viewModelScope.launch { settings.saveLastKb(kbName) }
    }

    fun show(tab: KbTab) {
        savedState[TAB] = tab.name
        viewModelScope.launch { settings.saveKbTab(kbName, tab.name) }
    }

    private companion object {
        const val TAB = "tab"
    }
}
