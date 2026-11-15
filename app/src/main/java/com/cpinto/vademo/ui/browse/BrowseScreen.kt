package com.cpinto.vademo.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Folder
import com.cpinto.vademo.kb.IndexedNote
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.NoteSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseState(
    val title: String = "",
    val folders: List<Folder> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    /** At the top of a knowledge base only, for the Recent, Types and Tags tabs; null while it is read. */
    val index: NoteIndex? = null,
)

/** The ways to list a knowledge base at its top level (`docs/kb-views-and-queries.md`, "Views"). */
private enum class View(val label: String) { Folders("Folders"), Recent("Recent"), Types("Types"), Tags("Tags") }

/** Collapsed into one row in the Types tab, since sources outnumber the notes you think with. */
private const val SOURCE = "source"

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val path: String = savedState["path"] ?: ""
    val isTop: Boolean = path.isEmpty()
    private val kb = repository.knowledgeBase(kbName)

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = if (path.isEmpty()) kb.root else kb.fileAt(path)
            // At the root only the index is a note; README, CLAUDE.md and the
            // backlog are repository files, not knowledge.
            val notes = if (path.isEmpty()) listOfNotNull(kb.index?.let(kb::summary)) else kb.notesIn(dir)
            _state.value = BrowseState(
                title = if (path.isEmpty()) kbName else path,
                folders = kb.foldersIn(dir),
                notes = notes,
            )
            // Read after the folders are on screen, since it opens every note.
            if (isTop) _state.value = _state.value.copy(index = NoteIndex.of(kb))
        }
    }

    fun pathOf(folder: Folder): String = kb.relativePath(folder.dir)

    fun pathOf(note: NoteSummary): String = kb.relativePath(note.file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onOpenFolder: (kb: String, path: String) -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    onOpenMap: (kb: String) -> Unit,
    onOpenDiagrams: (kb: String) -> Unit,
    onSearch: (kb: String, filters: String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var view by rememberSaveable { mutableStateOf(View.Folders) }
    val kbName = viewModel.kbName
    val openNote = { note: IndexedNote -> onOpenNote(kbName, note.path) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSearch(viewModel.kbName, "") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { onOpenDiagrams(viewModel.kbName) }) {
                        Icon(Icons.Outlined.AccountTree, contentDescription = "Diagrams")
                    }
                    IconButton(onClick = { onOpenMap(viewModel.kbName) }) {
                        Icon(Icons.Outlined.Hub, contentDescription = "Map of notes")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.isTop) {
                SecondaryTabRow(selectedTabIndex = view.ordinal) {
                    View.entries.forEach {
                        Tab(selected = view == it, onClick = { view = it }, text = { Text(it.label) })
                    }
                }
            }
            val index = state.index
            when {
                view == View.Folders -> FolderList(state, viewModel, onOpenFolder, onOpenNote)
                index == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                view == View.Recent -> LazyColumn(Modifier.fillMaxSize()) {
                    items(index.recent(), key = { it.path }) { note ->
                        NoteRow(note, listOfNotNull(note.updated, note.type), openNote)
                    }
                }
                view == View.Types -> LazyColumn(Modifier.fillMaxSize()) {
                    for ((type, notes) in index.byType()) {
                        if (type == SOURCE) {
                            item(key = "type:$type") {
                                ListItem(
                                    modifier = Modifier.clickable { onSearch(kbName, "type:$SOURCE") },
                                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = null) },
                                    headlineContent = { Text("Sources") },
                                    supportingContent = { Text(count(notes.size)) },
                                )
                                HorizontalDivider()
                            }
                            continue
                        }
                        item(key = "type:$type") {
                            Text(
                                "${type ?: "No type"} (${notes.size})",
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        items(notes, key = { "type:$type:" + it.path }) { note ->
                            NoteRow(note, listOfNotNull(note.status), openNote)
                        }
                    }
                }
                else -> {
                    val tags = index.tagCounts()
                    if (tags.isEmpty()) {
                        Text(
                            "No note has tags.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(tags, key = { it.first }) { (tag, n) ->
                            ListItem(
                                modifier = Modifier.clickable { onSearch(kbName, "tag:$tag") },
                                leadingContent = { Icon(Icons.Outlined.Sell, contentDescription = null) },
                                headlineContent = { Text(tag) },
                                supportingContent = { Text(count(n)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private fun count(n: Int) = if (n == 1) "1 note" else "$n notes"

@Composable
private fun NoteRow(note: IndexedNote, details: List<String>, onOpen: (IndexedNote) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onOpen(note) },
        leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
        headlineContent = { Text(note.title) },
        supportingContent = details.takeIf { it.isNotEmpty() }?.let { { Text(it.joinToString(" · ")) } },
    )
    HorizontalDivider()
}

@Composable
private fun FolderList(
    state: BrowseState,
    viewModel: BrowseViewModel,
    onOpenFolder: (kb: String, path: String) -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.notes, key = { "note:" + it.file.path }) { note ->
            ListItem(
                modifier = Modifier.clickable { onOpenNote(viewModel.kbName, viewModel.pathOf(note)) },
                leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                headlineContent = { Text(note.title) },
                supportingContent = listOfNotNull(note.type, note.status)
                    .takeIf { it.isNotEmpty() }
                    ?.let { { Text(it.joinToString(" · ")) } },
            )
            HorizontalDivider()
        }
        items(state.folders, key = { "folder:" + it.dir.path }) { folder ->
            ListItem(
                modifier = Modifier.clickable { onOpenFolder(viewModel.kbName, viewModel.pathOf(folder)) },
                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                headlineContent = { Text(folder.name) },
                supportingContent = { Text(count(folder.noteCount)) },
            )
            HorizontalDivider()
        }
    }
}
