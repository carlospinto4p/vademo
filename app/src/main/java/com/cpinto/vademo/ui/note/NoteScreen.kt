package com.cpinto.vademo.ui.note

import com.cpinto.vademo.ui.BackForward
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.WikiLinks
import com.cpinto.vademo.ui.diagram.DiagramCard
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    onEdit: (kb: String, path: String) -> Unit,
    onOpenDiagram: (kb: String, name: String) -> Unit,
    onSearch: (kb: String, filters: String) -> Unit,
    viewModel: NoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val external = LocalUriHandler.current
    val linkHandler = remember(external) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val target = WikiLinks.targetOf(uri)
                val diagram = WikiLinks.diagramOf(uri)
                when {
                    diagram != null -> onOpenDiagram(viewModel.kbName, diagram)
                    target == null -> {
                        // openUri throws when no app takes the link, so a bad link would close the app.
                        val opened = WikiLinks.opensOutside(uri) && runCatching { external.openUri(uri) }.isSuccess
                        if (!opened) scope.launch { snackbar.showSnackbar("Can't open this link: $uri") }
                    }
                    else -> scope.launch {
                        val found = viewModel.resolve(target)
                        if (found != null) onOpenNote(viewModel.kbName, found)
                        else snackbar.showSnackbar("No note named $target")
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1) },
                navigationIcon = { BackForward(onBack) },
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
        // Scaling the font scale and not the density reflows the text to the
        // screen's width, so zooming in never means panning sideways to read
        // a line. It covers the chips and the date as well as the body.
        val density = LocalDensity.current
        val zoomed = remember(density, textScale) {
            Density(density.density, density.fontScale * textScale)
        }
        CompositionLocalProvider(LocalDensity provides zoomed) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .pinchTextZoom(onZoom = viewModel::zoomBy, onZoomEnd = viewModel::saveTextScale)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.missing) {
                    Text("This note no longer exists. It may have been renamed or deleted on GitHub.")
                    return@Column
                }
                state.frontmatter?.let { front ->
                    // Each chip searches this knowledge base for the notes sharing it.
                    val filters = listOfNotNull(
                        front.type?.let { it to "type:$it" },
                        front.status?.let { it to "status:$it" },
                    ) + front.tags.map { it to "tag:$it" }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        filters.forEach { (label, filter) ->
                            AssistChip(onClick = { onSearch(viewModel.kbName, filter) }, label = { Text(label) })
                        }
                    }
                    front.updated?.let {
                        Text("Updated $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
                CompositionLocalProvider(LocalUriHandler provides linkHandler) {
                    // The renderer's default headings use Material's display sizes,
                    // which put a note title across most of a phone screen.
                    // Built once for all blocks. markdownTypography is composable,
                    // so it is called here rather than inside remember.
                    val type = MaterialTheme.typography
                    val typography = markdownTypography(
                        h1 = type.headlineSmall,
                        h2 = type.titleLarge,
                        h3 = type.titleMedium,
                        h4 = type.titleSmall,
                        h5 = type.titleSmall,
                        h6 = type.titleSmall,
                    )
                    val components = remember {
                        markdownComponents(
                            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
                            table = { NoteTable(it) },
                        )
                    }
                    for (block in state.blocks) {
                        when (block) {
                            is NoteBlock.Diagram ->
                                DiagramCard(block.name, block.file) { onOpenDiagram(viewModel.kbName, block.name) }
                            is NoteBlock.Text -> Markdown(
                                content = block.markdown,
                                typography = typography,
                                components = components,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.linkedFrom.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Linked from (${state.linkedFrom.size})", style = MaterialTheme.typography.titleMedium)
                    Column {
                        for (from in state.linkedFrom) {
                            ListItem(
                                modifier = Modifier.clickable { onOpenNote(viewModel.kbName, from.path) },
                                headlineContent = { Text(from.title) },
                                supportingContent = from.type?.let { { Text(it) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
