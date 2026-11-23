package com.cpinto.vademo.ui.kb

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.IndexedNote
import com.cpinto.vademo.ui.browse.BrowseScreen
import com.cpinto.vademo.ui.diagram.DiagramListScreen
import com.cpinto.vademo.ui.diagram.DiagramScreen
import com.cpinto.vademo.ui.graph.GraphScreen

/**
 * A knowledge base's first screen: a picture of it, its main diagram or its
 * map of notes, with the map, the diagrams and the notes one tap apart along
 * the bottom. Each tab keeps its own view model for as long as the knowledge
 * base is open, so coming back to the map does not lay it out again.
 */
@Composable
fun KbScreen(
    onBack: () -> Unit,
    onOpenFolder: (kb: String, path: String) -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    onOpenDiagram: (kb: String, name: String) -> Unit,
    onAllDiagrams: (kb: String) -> Unit,
    onSearch: (kb: String, filters: String) -> Unit,
    onNewNote: (kb: String, path: String, template: String, title: String) -> Unit,
    viewModel: KbViewModel = hiltViewModel(),
) {
    val tabName by viewModel.tab.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    // Again each time this screen comes back, as from the note just read.
    LaunchedEffect(Unit) { viewModel.loadRecent() }
    val tab = KbTab.parse(tabName) ?: KbTab.Map
    val kb = viewModel.kbName
    val search = { onSearch(kb, "") }

    Scaffold(
        bottomBar = {
            Column {
                if (recent.isNotEmpty()) ContinueReading(recent) { onOpenNote(kb, it.path) }
                NavigationBar {
                    KbTab.entries.forEach {
                        NavigationBarItem(
                            selected = it == tab,
                            onClick = { viewModel.show(it) },
                            icon = { Icon(iconOf(it), contentDescription = null) },
                            label = { Text(it.label) },
                        )
                    }
                }
            }
        },
        // The screen inside draws its own top bar under the status bar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                KbTab.Map -> GraphScreen(onBack = onBack, onOpenNote = onOpenNote, onSearch = search)
                KbTab.Diagrams ->
                    if (viewModel.diagram.isNotEmpty()) {
                        DiagramScreen(
                            onBack = onBack,
                            onOpenNote = onOpenNote,
                            onSearch = search,
                            onAllDiagrams = { onAllDiagrams(kb) },
                        )
                    } else {
                        DiagramListScreen(onBack = onBack, onOpen = onOpenDiagram, onSearch = search)
                    }
                KbTab.Notes -> BrowseScreen(
                    onBack = onBack,
                    onOpenFolder = onOpenFolder,
                    onOpenNote = onOpenNote,
                    onSearch = onSearch,
                    onNewNote = onNewNote,
                )
            }
        }
    }
}

/** The notes last opened here, newest first, one tap from opening again. */
@Composable
private fun ContinueReading(notes: List<IndexedNote>, onOpen: (IndexedNote) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Continue reading", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            notes.forEach { note ->
                SuggestionChip(
                    onClick = { onOpen(note) },
                    label = {
                        Text(note.title, Modifier.widthIn(max = 160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                )
            }
        }
    }
}

private fun iconOf(tab: KbTab): ImageVector = when (tab) {
    KbTab.Map -> Icons.Outlined.Hub
    KbTab.Diagrams -> Icons.Outlined.AccountTree
    KbTab.Notes -> Icons.Outlined.Folder
}
