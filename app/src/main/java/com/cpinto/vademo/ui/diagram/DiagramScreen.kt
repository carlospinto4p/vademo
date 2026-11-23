package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.ui.BackForward
import com.cpinto.vademo.ui.graph.Legend
import com.cpinto.vademo.ui.graph.Loading
import com.cpinto.vademo.ui.graph.PanZoom
import com.cpinto.vademo.ui.graph.StartAt

private val EXPORT_MARGIN = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    /** On a knowledge base's Diagrams tab: a Search item in the menu. */
    onSearch: (() -> Unit)? = null,
    /** On a knowledge base's Diagrams tab: an action that lists every diagram. */
    onAllDiagrams: (() -> Unit)? = null,
    viewModel: DiagramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    var open by remember { mutableStateOf<DiagramDialog?>(null) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val context = LocalContext.current
    // Every frame of the whole drawing, at its own size, is recorded here for Share as image.
    val picture = rememberGraphicsLayer()
    val paper = MaterialTheme.colorScheme.surface
    // While the picture is taken the drawing is shown once without the arrows' edit handles,
    // which are controls and not part of the diagram.
    var exporting by remember { mutableStateOf(false) }
    LaunchedEffect(exporting) {
        if (!exporting) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        DiagramShare.image(context, viewModel.name, picture.toImageBitmap())
        exporting = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selected.isNotEmpty()) SelectionBar(state.selected, viewModel, onOpen = { open = it })
            else MainBar(
                state,
                viewModel,
                onBack,
                onOpen = { open = it },
                onShareImage = { exporting = true },
                onShareMermaid = { state.diagram?.let { DiagramShare.mermaid(context, it) } },
                onSearch = onSearch,
                onAllDiagrams = onAllDiagrams,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val diagram = state.diagram
            when {
                state.error != null -> Text("This diagram could not be read: ${state.error}", Modifier.padding(16.dp))
                diagram == null -> Loading()
                diagram.nodes.isEmpty() -> Text(
                    if (state.editable) "This diagram has no boxes yet. Add one from the menu at the top right."
                    else "This diagram has no boxes yet.",
                    Modifier.padding(16.dp),
                )
                else -> {
                    val places = rememberPlaces(diagram, state.moves)
                    val colours = diagramColours()
                    // Kept across drag frames, so the boxes that did not move skip recomposing.
                    val actions = remember(viewModel, onOpenNote, state.editable, exporting, state.connectingFrom, state.selected) {
                        DiagramActions(
                            onTap = { id -> viewModel.tap(id)?.let { onOpenNote(viewModel.kbName, it) } },
                            onLongPress = if (state.editable) { id -> open = DiagramDialog.Actions(id) } else null,
                            onEdge = if (state.editable && !exporting) { id -> open = DiagramDialog.Edge(id) } else null,
                            highlighted = state.connectingFrom,
                            selected = state.selected,
                            onDrag = viewModel::drag,
                            onDragEnd = viewModel::dragEnded,
                        )
                    }
                    // Recorded with tyfold's export margin round it (`PADDING` in its graphExport.ts);
                    // the screen draws the recording back at the content's own place.
                    val recording = Modifier.drawWithContent {
                        val margin = EXPORT_MARGIN.toPx()
                        picture.record(size = IntSize((size.width + 2 * margin).toInt(), (size.height + 2 * margin).toInt())) {
                            drawRect(paper)
                            translate(margin, margin) { this@drawWithContent.drawContent() }
                        }
                        translate(-margin, -margin) { drawLayer(picture) }
                    }
                    // Opens where the flow starts, when it is too big to read whole.
                    val start = if (diagram.direction == Diagram.Direction.TB) StartAt.Top else StartAt.TopLeft
                    PanZoom(places.width.dp, places.height.dp, start = start, contentModifier = recording) {
                        DiagramContent(diagram, places, colours, state.notes, actions)
                    }
                    if (diagram.legend.isNotEmpty()) {
                        Legend(
                            diagram.legend.map { it.label to colours.stripe(diagram.hueOf(it.id)!!) },
                            Modifier.align(Alignment.TopEnd).padding(12.dp),
                            startOpen = false,
                        )
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                if (state.selected.isNotEmpty()) {
                    Banner("Tap boxes to add or remove them. Drag a selected box to move them all.")
                }
                if (state.connectingFrom != null) {
                    Banner("Tap the box the arrow goes to.", action = "Cancel", onAction = viewModel::cancelConnecting)
                }
                if (!state.editable && diagram != null) {
                    Banner("Editing is off until the sync conflict in this knowledge base is resolved.")
                }
                if (state.problems.isNotEmpty()) {
                    Banner("Not saved: " + state.problems.joinToString("; "))
                }
            }
        }
    }

    open?.let { dialog ->
        DiagramDialogs(dialog, viewModel, onChange = { open = it })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainBar(
    state: DiagramState,
    viewModel: DiagramViewModel,
    onBack: () -> Unit,
    onOpen: (DiagramDialog) -> Unit,
    onShareImage: () -> Unit,
    onShareMermaid: () -> Unit,
    onSearch: (() -> Unit)?,
    onAllDiagrams: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { BackForward(onBack) },
        actions = {
            if (onAllDiagrams != null) {
                IconButton(onClick = onAllDiagrams) {
                    Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "All diagrams")
                }
            }
            val diagram = state.diagram ?: return@TopAppBar
            val editable = state.editable
            if (editable) {
                IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val item = @Composable { text: String, enabled: Boolean, action: () -> Unit ->
                        DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = {
                            menu = false
                            action()
                        })
                    }
                    if (editable) {
                        item("Add box", true) {
                            viewModel.editReturning { it.addNode(group = false) }?.let { onOpen(DiagramDialog.EditNode(it)) }
                        }
                        item("Add group", true) {
                            viewModel.editReturning { it.addNode(group = true) }?.let { onOpen(DiagramDialog.EditNode(it)) }
                        }
                        item("Paste", state.canPaste, viewModel::paste)
                        item("Legend", true) { onOpen(DiagramDialog.Legend) }
                        item("Title and direction", true) { onOpen(DiagramDialog.Title) }
                    }
                    item("Tidy (forget where boxes were dragged)", state.moves.isNotEmpty(), viewModel::tidy)
                    item("Share as image", diagram.nodes.isNotEmpty(), onShareImage)
                    item("Share as Mermaid", true, onShareMermaid)
                    if (editable) item("Delete diagram", true) { onOpen(DiagramDialog.DeleteDiagram) }
                    if (onSearch != null) item("Search notes", true, onSearch)
                }
            }
        },
    )
}

/** The top bar while boxes are selected: what can be done to all of them at once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(selected: Set<String>, viewModel: DiagramViewModel, onOpen: (DiagramDialog) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(if (selected.size == 1) "1 selected" else "${selected.size} selected") },
        navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
                Icon(Icons.Filled.Close, contentDescription = "End selection")
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions on selected")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val item = @Composable { text: String, action: () -> Unit ->
                        DropdownMenuItem(text = { Text(text) }, onClick = {
                            menu = false
                            action()
                        })
                    }
                    item("Group") {
                        val group = viewModel.editReturning { it.groupAround(selected) }
                        viewModel.clearSelection()
                        group?.let { onOpen(DiagramDialog.EditNode(it)) }
                    }
                    item("Move to group…") { onOpen(DiagramDialog.MoveTo(selected)) }
                    item("Status…") { onOpen(DiagramDialog.Status(selected)) }
                    item("Category…") { onOpen(DiagramDialog.Category(selected)) }
                    item("Copy") { viewModel.copy(selected) }
                    item("Cut") { viewModel.cut(selected) }
                    item("Duplicate") { viewModel.duplicate(selected) }
                    item("Delete") { viewModel.editBoxes(selected) { d, ids -> d.removeNodes(ids) } }
                }
            }
        },
    )
}

@Composable
private fun Banner(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
