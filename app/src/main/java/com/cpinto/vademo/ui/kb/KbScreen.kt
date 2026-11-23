package com.cpinto.vademo.ui.kb

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val tab = KbTab.parse(tabName) ?: KbTab.Map
    val kb = viewModel.kbName
    val search = { onSearch(kb, "") }

    Scaffold(
        bottomBar = {
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

private fun iconOf(tab: KbTab): ImageVector = when (tab) {
    KbTab.Map -> Icons.Outlined.Hub
    KbTab.Diagrams -> Icons.Outlined.AccountTree
    KbTab.Notes -> Icons.Outlined.Folder
}
