package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.cpinto.vademo.kb.DiagramFile
import com.cpinto.vademo.kb.diagramsIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagramListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val kb = repository.knowledgeBase(kbName)

    /** Null until read. */
    private val _diagrams = MutableStateFlow<List<DiagramFile>?>(null)
    val diagrams: StateFlow<List<DiagramFile>?> = _diagrams

    fun load() {
        viewModelScope.launch(Dispatchers.IO) { _diagrams.value = diagramsIn(kb.root) }
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
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagrams") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val list = diagrams ?: return@Scaffold
        if (list.isEmpty()) {
            Text(
                "No diagrams yet. They are the files in this knowledge base's .tyfold-graphs folder, " +
                    "drawn by agents with tyfold's graph tool.",
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
