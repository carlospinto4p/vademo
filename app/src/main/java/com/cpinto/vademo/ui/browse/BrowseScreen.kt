package com.cpinto.vademo.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.Folder
import com.cpinto.vademo.kb.Health
import com.cpinto.vademo.kb.IndexedNote
import com.cpinto.vademo.kb.NoteTemplate
import com.cpinto.vademo.ui.BackForward

/** The ways to list a knowledge base at its top level (`docs/kb-views-and-queries.md`, "Views"). */
private enum class View(val label: String) {
    Folders("Folders"), Recent("Recent"), Types("Types"), Tags("Tags"), Health("Health")
}

/** Collapsed into one row in the Types tab, since sources outnumber the notes you think with. */
private const val SOURCE = "source"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onOpenFolder: (kb: String, path: String) -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    onSearch: (kb: String, filters: String) -> Unit,
    onNewNote: (kb: String, path: String, template: String, title: String) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    var view by rememberSaveable { mutableStateOf(View.Folders) }
    var newNote by rememberSaveable { mutableStateOf(false) }
    val kbName = viewModel.kbName
    val openNote = { note: IndexedNote -> onOpenNote(kbName, note.path) }

    if (newNote) {
        NewNoteDialog(
            templates = state.templates,
            onDismiss = { newNote = false },
            onCreate = { template, title, name, onError ->
                viewModel.startNote(template, name, onError) { path, templatePath ->
                    newNote = false
                    onNewNote(kbName, path, templatePath, title)
                }
            },
        )
    }

    Scaffold(
        floatingActionButton = {
            if (state.templates.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { newNote = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("New note") },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = { BackForward(onBack) },
                actions = {
                    IconButton(onClick = { onSearch(viewModel.kbName, "") }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (viewModel.isTop) {
                SecondaryScrollableTabRow(selectedTabIndex = view.ordinal, edgePadding = 0.dp) {
                    View.entries.forEach {
                        Tab(selected = view == it, onClick = { view = it }, text = { Text(it.label) })
                    }
                }
            }
            val index = state.index
            when {
                view == View.Folders -> FolderList(state, viewModel, onOpenFolder, onOpenNote)
                index == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
                // Recent, Types and Tags sort or group every note, so once per index.
                view == View.Recent -> {
                    val recent = remember(index) { index.recent() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(recent, key = { it.path }) { note ->
                            NoteRow(note, listOfNotNull(note.updated, note.type), openNote)
                        }
                    }
                }
                view == View.Types -> {
                    val byType = remember(index) { index.byType() }
                    LazyColumn(Modifier.fillMaxSize()) {
                        for ((type, notes) in byType) {
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
                }
                view == View.Health -> {
                    // Runs five searches over every note, so not on each recomposition.
                    val health = remember(index) { Health.counts(index) }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(health, key = { it.first.query }) { (check, n) ->
                            ListItem(
                                modifier = Modifier.clickable { onSearch(kbName, check.query) },
                                leadingContent = {
                                    Icon(
                                        if (n == 0) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                    )
                                },
                                headlineContent = { Text(check.name) },
                                supportingContent = { Text(if (n == 0) "None" else count(n)) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
                else -> {
                    val tags = remember(index) { index.tagCounts() }
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
    if (state.loaded && state.notes.isEmpty() && state.folders.isEmpty()) {
        Text(
            if (state.templates.isNotEmpty()) "This folder is empty. Start a note with New note." else "This folder is empty.",
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    // Room under the last row for the New note button.
    val bottom = if (state.templates.isNotEmpty()) 88.dp else 0.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        items(state.notes, key = { "note:" + it.file.path }) { note ->
            // A note arriving with a sync slides in, rather than the rows jumping.
            Column(Modifier.animateItem()) {
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
        }
        items(state.folders, key = { "folder:" + it.dir.path }) { folder ->
            Column(Modifier.animateItem()) {
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
}

/**
 * Picks a template and a title for a new note. The file name follows the
 * title in kebab-case until it is edited by hand, for a `src-` prefix say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewNoteDialog(
    templates: List<String>,
    onDismiss: () -> Unit,
    onCreate: (template: String, title: String, name: String, onError: (String) -> Unit) -> Unit,
) {
    var template by rememberSaveable { mutableStateOf(if ("note" in templates) "note" else templates.first()) }
    var title by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Template", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.forEach {
                        FilterChip(selected = it == template, onClick = { template = it }, label = { Text(it) })
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (!nameEdited) name = NoteTemplate.fileName(it)
                        error = null
                    },
                    label = { Text("Title") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameEdited = true
                        error = null
                    },
                    label = { Text("File name") },
                    suffix = { Text(".md") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(template, title.trim(), name, { error = it }) },
                enabled = title.isNotBlank() && name.isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
