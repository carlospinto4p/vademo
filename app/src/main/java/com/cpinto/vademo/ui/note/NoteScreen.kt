package com.cpinto.vademo.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Frontmatter
import com.cpinto.vademo.kb.FrontmatterParser
import com.cpinto.vademo.kb.WikiLinks
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownTypography
import androidx.compose.ui.Alignment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteState(
    val title: String = "",
    val frontmatter: Frontmatter? = null,
    val markdown: String = "",
    val missing: Boolean = false,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    val path: String = checkNotNull(savedState["path"])
    private val kb = repository.knowledgeBase(kbName)

    private val _state = MutableStateFlow(NoteState())
    val state: StateFlow<NoteState> = _state

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = kb.fileAt(path)
            if (!file.isFile) {
                _state.value = NoteState(title = path, missing = true)
                return@launch
            }
            val parsed = FrontmatterParser.parse(file.readText())
            _state.value = NoteState(
                title = parsed.frontmatter?.title ?: file.nameWithoutExtension,
                frontmatter = parsed.frontmatter,
                markdown = WikiLinks.toMarkdown(parsed.body),
            )
        }
    }

    /** Path of the note a `[[target]]` link points at, or `null` when no such note exists. */
    fun resolve(target: String): String? = kb.resolve(target)?.let(kb::relativePath)
}

/**
 * The renderer's table cuts every cell to one line with an ellipsis, which
 * hides the reasoning column of a scorecard. Cells here wrap instead.
 */
@Composable
private fun WrappingTable(model: MarkdownComponentModel) {
    MarkdownTable(
        content = model.content,
        node = model.node,
        style = model.typography.table,
        headerBlock = { content, header, width, style ->
            MarkdownTableHeader(content, header, width, style, verticalAlignment = Alignment.Top, maxLines = Int.MAX_VALUE)
        },
        rowBlock = { content, row, width, style ->
            MarkdownTableRow(content, row, width, style, verticalAlignment = Alignment.Top, maxLines = Int.MAX_VALUE)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    onEdit: (kb: String, path: String) -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val external = LocalUriHandler.current
    val linkHandler = remember(external) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val target = WikiLinks.targetOf(uri)
                when {
                    target == null -> external.openUri(uri)
                    else -> viewModel.resolve(target)?.let { onOpenNote(viewModel.kbName, it) }
                        ?: scope.launch { snackbar.showSnackbar("No note named $target") }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.missing) {
                        IconButton(onClick = { onEdit(viewModel.kbName, viewModel.path) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.missing) {
                Text("This note no longer exists. It may have been renamed or deleted on GitLab.")
                return@Column
            }
            state.frontmatter?.let { front ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(front.type, front.status).plus(front.tags).forEach {
                        AssistChip(onClick = {}, label = { Text(it) })
                    }
                }
                front.updated?.let {
                    Text("Updated $it", style = MaterialTheme.typography.bodySmall)
                }
            }
            CompositionLocalProvider(LocalUriHandler provides linkHandler) {
                // The renderer's default headings use Material's display sizes,
                // which put a note title across most of a phone screen.
                val type = MaterialTheme.typography
                Markdown(
                    content = state.markdown,
                    typography = markdownTypography(
                        h1 = type.headlineSmall,
                        h2 = type.titleLarge,
                        h3 = type.titleMedium,
                        h4 = type.titleSmall,
                        h5 = type.titleSmall,
                        h6 = type.titleSmall,
                    ),
                    components = markdownComponents(
                        checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
                        table = { WrappingTable(it) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
