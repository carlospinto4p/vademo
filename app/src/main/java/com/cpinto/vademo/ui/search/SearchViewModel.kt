package com.cpinto.vademo.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.kb.Health
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.NoteSearch
import com.cpinto.vademo.kb.SavedQuery
import com.cpinto.vademo.kb.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val loading: Boolean = true,
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    /** More than one knowledge base is searched, so each hit names its own. */
    val showKb: Boolean = false,
    /** The user's saved searches; the Health checks come before them and are not stored. */
    val saved: List<SavedQuery> = emptyList(),
) {
    /** Nothing asked yet beyond which knowledge base: the saved searches are offered instead of results. */
    val offersSaved: Boolean
        get() = NoteSearch.parse(query).asksNothing
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** The text the field starts with: empty from the home screen, `kb:<name> ` from a knowledge base. */
    val initialQuery: String = savedState["q"] ?: ""

    private val indexes = MutableStateFlow<List<NoteIndex>?>(null)
    private val query = MutableStateFlow(initialQuery)

    // Searched once typing pauses, not on every keystroke: each search reads every note's text.
    // The text the screen opens with is searched at once. The rest of the state, such as the
    // Save button, follows the field at once. A query that asks nothing finds nothing, so the
    // notes it would match never flash up while the first word is typed.
    @OptIn(FlowPreview::class)
    private val hits = combine(indexes, merge(query.take(1), query.drop(1).debounce(TYPING_PAUSE_MS))) { all, text ->
        if (all == null || NoteSearch.parse(text).asksNothing) emptyList() else NoteSearch.search(all, text)
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<SearchState> = combine(indexes, query, hits, settings.savedQueries) { all, text, found, saved ->
        if (all == null) {
            SearchState(loading = true, query = text, saved = saved)
        } else {
            SearchState(
                loading = false,
                query = text,
                hits = found,
                showKb = all.size > 1,
                saved = saved,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchState(query = initialQuery))

    fun saveQuery(name: String) {
        val text = query.value
        viewModelScope.launch { settings.saveQuery(name, text) }
    }

    fun deleteQuery(name: String) {
        viewModelScope.launch { settings.deleteQuery(name) }
    }

    init {
        // Fetched each time the screen opens, so it always reflects the last sync.
        viewModelScope.launch(Dispatchers.IO) {
            indexes.value = repository.indexes()
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    private companion object {
        const val TYPING_PAUSE_MS = 150L
    }
}
