package com.cpinto.vademo.ui.kb

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])

    /** The main diagram's name, shown on the Diagrams tab; empty when there is none. */
    val diagram: String = savedState["name"] ?: ""

    /** The name of the tab showing, the route's `tab` until another is chosen. */
    val tab: StateFlow<String> = savedState.getStateFlow(TAB, KbTab.Map.name)

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
