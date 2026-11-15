package com.cpinto.vademo.ui.diagram

import com.cpinto.vademo.ui.BackForward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramFile
import com.cpinto.vademo.kb.createDiagram
import com.cpinto.vademo.kb.diagramsIn
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagramListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val kb = repository.knowledgeBase(kbName)

    /** Null until read. */
    private val _diagrams = MutableStateFlow<List<DiagramFile>?>(null)
    val diagrams: StateFlow<List<DiagramFile>?> = _diagrams

    /** False while a sync conflict waits in this knowledge base, as for editing notes. */
    private val _editable = MutableStateFlow(false)
    val editable: StateFlow<Boolean> = _editable

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _diagrams.value = diagramsIn(kb.root)
            _editable.value = repository.list().none { it.name == kbName && it.needsChoice }
        }
    }

    /** Writes a new, empty diagram, then [onCreated] with its name so the screen can open it. */
    fun create(title: String, direction: Diagram.Direction, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.list().any { it.name == kbName && it.needsChoice }) return@launch
            val name = runCatching { createDiagram(kb.root, title, direction) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { onCreated(name) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramListScreen(
    onBack: () -> Unit,
    onOpen: (kb: String, name: String) -> Unit,
    viewModel: DiagramListViewModel = hiltViewModel(),
) {
    val diagrams by viewModel.diagrams.collectAsStateWithLifecycle()
    val editable by viewModel.editable.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NewDiagramDialog(onDismiss = { creating = false }) { title, direction ->
            creating = false
            viewModel.create(title, direction) { onOpen(viewModel.kbName, it) }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (editable) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    // The label's text does not reach TalkBack on this button, so the icon carries it.
                    icon = { Icon(Icons.Filled.Add, contentDescription = "New diagram") },
                    text = { Text("New diagram") },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("Diagrams") },
                navigationIcon = { BackForward(onBack) },
            )
        },
    ) { padding ->
        val list = diagrams ?: return@Scaffold
        if (list.isEmpty()) {
            Text(
                "No diagrams yet. They are the files in this knowledge base's .tyfold-graphs folder: " +
                    "start one with New diagram, or have an agent draw one with tyfold's graph tool.",
                Modifier.padding(padding).padding(16.dp),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(list, key = { it.name }) { file ->
                val diagram = file.diagram
                ListItem(
                    modifier = if (diagram != null) Modifier.clickable { onOpen(viewModel.kbName, file.name) } else Modifier,
                    leadingContent = {
                        Icon(if (diagram != null) Icons.Outlined.AccountTree else Icons.Outlined.ErrorOutline, contentDescription = null)
                    },
                    headlineContent = { Text(file.title) },
                    supportingContent = {
                        Text(
                            if (diagram == null) "Could not be read: ${file.error}"
                            else diagram.nodes.count { !it.isGroup }.let { if (it == 1) "1 box" else "$it boxes" },
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NewDiagramDialog(onDismiss: () -> Unit, onCreate: (String, Diagram.Direction) -> Unit) {
    var title by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(Diagram.Direction.LR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New diagram") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true)
                DirectionChoice(direction) { direction = it }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title, direction) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
