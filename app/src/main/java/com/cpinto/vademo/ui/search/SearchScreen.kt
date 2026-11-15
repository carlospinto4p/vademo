package com.cpinto.vademo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.NoteSearch
import com.cpinto.vademo.kb.SearchHit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchState(
    val loading: Boolean = true,
    val query: String = "",
    val hits: List<SearchHit> = emptyList(),
    /** More than one knowledge base is searched, so each hit names its own. */
    val showKb: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    /** The text the field starts with: empty from the home screen, `kb:<name> ` from a knowledge base. */
    val initialQuery: String = savedState["q"] ?: ""

    private val indexes = MutableStateFlow<List<NoteIndex>?>(null)
    private val query = MutableStateFlow(initialQuery)

    val state: StateFlow<SearchState> = combine(indexes, query) { all, text ->
        if (all == null) {
            SearchState(loading = true, query = text)
        } else {
            SearchState(loading = false, query = text, hits = NoteSearch.search(all, text), showKb = all.size > 1)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, SearchState(query = initialQuery))

    init {
        // Built each time the screen opens, so it always reflects the last sync.
        viewModelScope.launch(Dispatchers.IO) {
            indexes.value = repository.downloaded().map(NoteIndex::of)
        }
    }

    fun setQuery(text: String) {
        query.value = text
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(viewModel.initialQuery, TextRange(viewModel.initialQuery.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    viewModel.setQuery(it.text)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (field.text.isEmpty()) null else {
                    {
                        IconButton(onClick = {
                            field = TextFieldValue("")
                            viewModel.setQuery("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                NoteSearch.parse(state.query).isEmpty -> Hint(
                    "Words in titles, headings or text, and filters: type:question status:draft tag:raft " +
                        "updated:>2026-09-01 kb:name links:note is:orphan is:unverified",
                )
                state.hits.isEmpty() -> Hint("No notes match.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.hits, key = { it.kb + "/" + it.note.path }) { hit ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenNote(hit.kb, hit.note.path) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            headlineContent = { Text(hit.note.title) },
                            supportingContent = listOfNotNull(
                                hit.kb.takeIf { state.showKb },
                                hit.note.type,
                                hit.note.status,
                            ).takeIf { it.isNotEmpty() }?.let { { Text(it.joinToString(" · ")) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
