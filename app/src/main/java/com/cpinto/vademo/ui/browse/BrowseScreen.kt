package com.cpinto.vademo.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Folder
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
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val path: String = savedState["path"] ?: ""
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
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
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
                    supportingContent = { Text(if (folder.noteCount == 1) "1 note" else "${folder.noteCount} notes") },
                )
                HorizontalDivider()
            }
        }
    }
}
