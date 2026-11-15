package com.cpinto.vademo.ui.search

import com.cpinto.vademo.ui.BackForward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
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
import com.cpinto.vademo.data.SavedQueries
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.kb.Health
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.SavedQuery
import com.cpinto.vademo.kb.NoteSearch
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.take
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
    // Opened from a tag or a type, the results are what was asked for, so
    // the keyboard stays down; with nothing but `kb:` there is only typing to do.
    LaunchedEffect(Unit) {
        if (NoteSearch.parse(viewModel.initialQuery).asksNothing) focus.requestFocus()
    }
    val search = { text: String ->
        field = TextFieldValue(text, TextRange(text.length))
        viewModel.setQuery(text)
    }
    var naming by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (naming) {
        NameDialog(
            onDismiss = { naming = false },
            onSave = { name ->
                naming = false
                viewModel.saveQuery(name)
                scope.launch { snackbar.showSnackbar("Saved as \"$name\"") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = { BackForward(onBack) },
                actions = {
                    if (!state.offersSaved) {
                        IconButton(onClick = { naming = true }) {
                            Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save search")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
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
                        IconButton(onClick = { search("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
            )
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                state.offersSaved -> LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "saved") { Heading("Saved searches") }
                    val pick = { saved: SavedQuery -> search(SavedQueries.applied(saved, viewModel.initialQuery)) }
                    items(Health.CHECKS, key = { "check:" + it.name }) { SavedRow(it, pick, onDelete = null) }
                    items(state.saved, key = { "own:" + it.name }) { SavedRow(it, pick, viewModel::deleteQuery) }
                    item(key = "hint") {
                        Hint(
                            "Words in titles, headings or text, and filters: type:question status:draft tag:raft " +
                                "updated:>2026-09-01 kb:name folder:inbox links:note is:orphan is:unverified " +
                                "is:broken. Save a search with the bookmark at the top.",
                        )
                    }
                }
                state.hits.isEmpty() -> Hint("No notes match.")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val listsBroken = NoteSearch.Filter.Is("broken") in NoteSearch.parse(state.query).filters
                    items(state.hits, key = { it.kb + "/" + it.note.path }) { hit ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenNote(hit.kb, hit.note.path) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            headlineContent = { Text(hit.note.title) },
                            supportingContent = listOfNotNull(
                                hit.kb.takeIf { state.showKb },
                                hit.note.type,
                                hit.note.status,
                                ("No note named " + hit.missing.joinToString(", ")).takeIf { listsBroken },
                            ).takeIf { it.isNotEmpty() }?.let { { Text(it.joinToString(" · ")) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** A saved search; [onDelete] is null for the Health checks, which are built in. */
@Composable
private fun SavedRow(saved: SavedQuery, onPick: (SavedQuery) -> Unit, onDelete: ((String) -> Unit)?) {
    ListItem(
        modifier = Modifier.clickable { onPick(saved) },
        leadingContent = { Icon(Icons.Outlined.Bookmark, contentDescription = null) },
        headlineContent = { Text(saved.name) },
        supportingContent = { Text(saved.query) },
        trailingContent = onDelete?.let { delete ->
            {
                IconButton(onClick = { delete(saved.name) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${saved.name}")
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save search") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
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
