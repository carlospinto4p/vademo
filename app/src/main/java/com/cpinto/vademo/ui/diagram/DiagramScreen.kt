package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.input.pointer.pointerInput
import com.cpinto.vademo.kb.Position
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramEdge
import com.cpinto.vademo.kb.DiagramLayout
import com.cpinto.vademo.kb.DiagramNode
import com.cpinto.vademo.kb.DiagramPlaces
import com.cpinto.vademo.kb.EdgeRoute
import com.cpinto.vademo.kb.Placed
import com.cpinto.vademo.kb.Ref
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cpinto.vademo.ui.graph.Legend
import com.cpinto.vademo.ui.graph.PanZoom
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val EXPORT_MARGIN = 48.dp
private val NODE_WIDTH = 200.dp
private val NODE_PADDING = 10.dp
private val STRIPE = 4.dp
private val GAP = 4.dp

/** What a tap and a long press on the drawing do. Without it (a diagram inside a note) nothing in it answers. */
internal class DiagramActions(
    val onTap: (id: String) -> Unit,
    /** Null when the diagram cannot be edited. */
    val onLongPress: ((id: String) -> Unit)?,
    val onEdge: ((id: String) -> Unit)?,
    /** The box an arrow is being drawn from, drawn highlighted. */
    val highlighted: String?,
    /** Selected boxes, drawn outlined; dragging one moves them all. */
    val selected: Set<String> = emptySet(),
    /** A drag of a selected box, in dp; null when nothing can be dragged. */
    val onDrag: ((dx: Float, dy: Float) -> Unit)? = null,
    val onDragEnd: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
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
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val diagram = state.diagram
            when {
                state.error != null -> Text("This diagram could not be read: ${state.error}", Modifier.padding(16.dp))
                diagram == null -> Unit
                diagram.nodes.isEmpty() -> Text(
                    if (state.editable) "This diagram has no boxes yet. Add one from the menu at the top right."
                    else "This diagram has no boxes yet.",
                    Modifier.padding(16.dp),
                )
                else -> {
                    val places = rememberPlaces(diagram, state.moves)
                    val colours = diagramColours()
                    val actions = DiagramActions(
                        onTap = { id -> viewModel.tap(id)?.let { onOpenNote(viewModel.kbName, it) } },
                        onLongPress = if (state.editable) { id -> open = DiagramDialog.Actions(id) } else null,
                        onEdge = if (state.editable && !exporting) { id -> open = DiagramDialog.Edge(id) } else null,
                        highlighted = state.connectingFrom,
                        selected = state.selected,
                        onDrag = viewModel::drag,
                        onDragEnd = viewModel::dragEnded,
                    )
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
                    PanZoom(places.width.dp, places.height.dp, contentModifier = recording) {
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
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
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
                    item("Delete") { viewModel.editSelected { d, ids -> d.removeNodes(ids) } }
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

/** The text styles of a box, shared by the measuring and the drawing so the two agree. */
private class BoxStyles(val label: TextStyle, val line: TextStyle, val notes: TextStyle)

@Composable
private fun boxStyles(): BoxStyles {
    val type = MaterialTheme.typography
    return BoxStyles(label = type.labelLarge, line = type.labelSmall, notes = type.bodySmall)
}

/**
 * The first line of a box: its tag, its status and its legend entry, so the
 * colours are never the only sign. An entry with the box's own name is left
 * out, as a group named after its category would otherwise say it twice.
 */
private fun headline(node: DiagramNode, diagram: Diagram): String =
    listOfNotNull(
        node.tag,
        node.status?.name?.lowercase(),
        diagram.legend.firstOrNull { it.id == node.category }?.label?.takeIf { it != node.text },
    ).joinToString(" · ")

internal fun refText(ref: Ref): String = ref.line?.let { "${ref.path}:$it" } ?: ref.path

private const val LABEL_LINES = 3
private const val NOTES_LINES = 4

/** Measures every box's text, then lays the diagram out around those sizes, with the boxes [moves] dragged. */
@Composable
internal fun rememberPlaces(diagram: Diagram, moves: Map<String, Position> = emptyMap()): DiagramPlaces {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val styles = boxStyles()
    return remember(diagram, moves, density, styles.label, styles.notes) {
        with(density) {
            val sizes = diagram.nodes.filterNot { it.isGroup }.associate { node ->
                val inner = NODE_WIDTH - NODE_PADDING * 2 - (if (node.category != null) STRIPE + GAP else 0.dp)
                val constraints = Constraints(maxWidth = inner.roundToPx())
                fun height(text: String, style: TextStyle, lines: Int): Dp =
                    measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = lines, constraints = constraints)
                        .size.height.toDp()
                var height = NODE_PADDING * 2 + height(node.text, styles.label, LABEL_LINES)
                if (headline(node, diagram).isNotEmpty()) height += GAP + height("Ag", styles.line, 1)
                node.notes?.takeIf { it.isNotBlank() }?.let { height += GAP + height(it, styles.notes, NOTES_LINES) }
                if (node.ref != null) height += GAP + height("Ag", styles.line, 1)
                node.id to DiagramLayout.Box(NODE_WIDTH.value.toDouble(), (height + 2.dp).value.toDouble())
            }
            DiagramLayout.place(diagram, sizes, moves)
        }
    }
}

/** Status and legend colours, readable on the light and the dark theme. */
internal class DiagramColours(val dark: Boolean, val error: Color, val outline: Color) {
    fun stripe(hue: Float) = Color.hsl(hue, 0.55f, if (dark) 0.6f else 0.45f)
    fun tint(hue: Float) = Color.hsl(hue, if (dark) 0.3f else 0.6f, if (dark) 0.2f else 0.94f)
    fun status(status: DiagramNode.Status?): Color? = when (status) {
        DiagramNode.Status.ADDED -> Color.hsl(135f, 0.6f, if (dark) 0.55f else 0.35f)
        DiagramNode.Status.CHANGED -> Color.hsl(40f, 0.9f, if (dark) 0.6f else 0.45f)
        DiagramNode.Status.REMOVED -> error
        DiagramNode.Status.UNREACHABLE -> outline
        null -> null
    }
}

@Composable
internal fun diagramColours(): DiagramColours {
    val scheme = MaterialTheme.colorScheme
    return DiagramColours(scheme.surface.luminance() < 0.5f, scheme.error, scheme.outline)
}

/** The drawing. [actions] is null where nothing in it should answer a touch, as inside a note. */
@Composable
internal fun DiagramContent(
    diagram: Diagram,
    places: DiagramPlaces,
    colours: DiagramColours,
    notes: Map<String, String>,
    actions: DiagramActions?,
) {
    val byId = diagram.nodes.associateBy { it.id }
    val depth = { node: DiagramNode -> generateSequence(node) { it.parent?.let(byId::get) }.take(diagram.nodes.size + 1).count() }
    Box(Modifier.fillMaxSize()) {
        // Outer groups first, so a group inside another is drawn over it.
        diagram.nodes.filter { it.isGroup }.sortedBy(depth).forEach { group ->
            places.boxes[group.id]?.let { GroupBox(group, diagram, it, colours, notes[group.id] != null, actions) }
        }
        Edges(diagram, places, actions?.onEdge)
        diagram.nodes.filterNot { it.isGroup }.forEach { node ->
            places.boxes[node.id]?.let { NodeBox(node, diagram, it, colours, notes[node.id] != null, actions) }
        }
    }
}

private fun Modifier.at(place: Placed) =
    offset(place.x.dp, place.y.dp).size(place.width.dp, place.height.dp)

/**
 * Tap and long press on a box or group, when the drawing answers touches,
 * and a drag when it is selected. The drag is consumed, so the diagram does
 * not pan under the finger at the same time.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.touch(id: String, actions: DiagramActions?): Modifier {
    if (actions == null) return this
    val drag = actions.onDrag?.takeIf { id in actions.selected }
    return combinedClickable(
        onLongClickLabel = if (actions.onLongPress != null) "Edit" else null,
        onLongClick = actions.onLongPress?.let { { it(id) } },
        onClick = { actions.onTap(id) },
    ).then(
        if (drag == null) Modifier
        else Modifier.pointerInput(id, drag) {
            detectDragGestures(onDragEnd = actions.onDragEnd, onDragCancel = actions.onDragEnd) { change, amount ->
                change.consume()
                // Inside the zoomed drawing, so the amount is already in the drawing's own units.
                drag(amount.x.toDp().value, amount.y.toDp().value)
            }
        },
    )
}

@Composable
private fun outline(id: String, actions: DiagramActions?, status: Color?, plain: Color): Pair<Dp, Color> = when {
    actions?.highlighted == id || id in actions?.selected.orEmpty() -> 3.dp to MaterialTheme.colorScheme.primary
    status != null -> 2.dp to status
    else -> 1.dp to plain
}

@Composable
private fun NodeBox(
    node: DiagramNode,
    diagram: Diagram,
    place: Placed,
    colours: DiagramColours,
    isNote: Boolean,
    actions: DiagramActions?,
) {
    val styles = boxStyles()
    val hue = diagram.hueOf(node.category)
    val shape = RoundedCornerShape(8.dp)
    val (width, border) = outline(node.id, actions, colours.status(node.status), MaterialTheme.colorScheme.outlineVariant)
    Surface(
        modifier = Modifier
            .at(place)
            .border(width, border, shape)
            .touch(node.id, actions),
        shape = shape,
        color = hue?.let(colours::tint) ?: MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row {
            if (hue != null) Box(Modifier.width(STRIPE).fillMaxHeight().background(colours.stripe(hue)))
            Column(
                // With a stripe the text is STRIPE + GAP narrower, as rememberPlaces measured it.
                Modifier.padding(start = if (hue != null) NODE_PADDING + GAP else NODE_PADDING, end = NODE_PADDING)
                    .padding(vertical = NODE_PADDING),
                verticalArrangement = Arrangement.spacedBy(GAP),
            ) {
                headline(node, diagram).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = styles.line, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    node.text,
                    style = styles.label,
                    maxLines = LABEL_LINES,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (node.status == DiagramNode.Status.REMOVED) TextDecoration.LineThrough else null,
                )
                node.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = styles.notes, maxLines = NOTES_LINES, overflow = TextOverflow.Ellipsis)
                }
                node.ref?.let { RefLine(it, isNote, styles.line) }
            }
        }
    }
}

/** A ref that names a note is drawn as a link; any other path is plain text, since the phone cannot open it. */
@Composable
private fun RefLine(ref: Ref, isNote: Boolean, style: TextStyle) {
    Text(
        refText(ref),
        style = style,
        color = if (isNote) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = if (isNote) TextDecoration.Underline else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GroupBox(
    group: DiagramNode,
    diagram: Diagram,
    place: Placed,
    colours: DiagramColours,
    isNote: Boolean,
    actions: DiagramActions?,
) {
    val styles = boxStyles()
    val hue = diagram.hueOf(group.category)
    val shape = RoundedCornerShape(12.dp)
    val (width, border) = outline(group.id, actions, colours.status(group.status), MaterialTheme.colorScheme.outline)
    Box(
        Modifier
            .at(place)
            .background(hue?.let(colours::tint)?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), shape)
            .border(width, border, shape)
            .touch(group.id, actions),
    ) {
        if (hue != null) {
            Box(Modifier.padding(vertical = 12.dp).width(STRIPE).fillMaxHeight().background(colours.stripe(hue)))
        }
        Column(Modifier.padding(horizontal = DiagramLayout.PAD_SIDE.dp, vertical = 6.dp)) {
            val title = listOfNotNull(group.text, headline(group, diagram).ifEmpty { null }).joinToString(" · ")
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (group.status == DiagramNode.Status.REMOVED) TextDecoration.LineThrough else null,
            )
            val second = group.notes?.takeIf { it.isNotBlank() } ?: group.ref?.let(::refText)
            second?.let {
                Text(
                    it,
                    style = styles.line,
                    color = if (isNote && group.notes.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Places a child centred on ([x], [y]) inside the drawing. */
private fun Modifier.centredAt(x: Dp, y: Dp, maxWidth: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth.roundToPx()))
    layout(placeable.width, placeable.height) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

/** One arrow as drawn: straight between [start] and [end], or round a box along [detour]. */
private class DrawnEdge(val edge: DiagramEdge, val start: Position, val end: Position, val detour: EdgeRoute.Detour?) {
    val label: Position get() = detour?.label ?: Position((start.x + end.x) / 2, (start.y + end.y) / 2)
}

/**
 * The arrows, with their labels at their middles, or on the bend of an arrow
 * that goes round a box ([EdgeRoute]). When [onEdge] is given each arrow can be
 * tapped there, on its label or, without one, on a small handle, to edit it.
 */
@Composable
private fun Edges(diagram: Diagram, places: DiagramPlaces, onEdge: ((String) -> Unit)?) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    val drawn = remember(diagram, places) {
        diagram.edges.mapNotNull { edge ->
            val a = places.boxes[edge.source] ?: return@mapNotNull null
            val b = places.boxes[edge.target] ?: return@mapNotNull null
            if (edge.source == edge.target) return@mapNotNull null
            val detour = EdgeRoute.detour(a, b, diagram.direction, EdgeRoute.obstacles(diagram, places, edge.source, edge.target))
            DrawnEdge(edge, detour?.start ?: EdgeRoute.exit(a, b), detour?.end ?: EdgeRoute.exit(b, a), detour)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val arrow = 10.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        fun px(p: Position) = Offset(p.x.toFloat() * density, p.y.toFloat() * density)
        for (line in drawn) {
            val start = px(line.start)
            val end = px(line.end)
            val path = Path().apply {
                moveTo(start.x, start.y)
                line.detour?.let { d ->
                    px(d.bendStart).let { lineTo(it.x, it.y) }
                    val (c1, c2, to) = listOf(px(d.control1), px(d.control2), px(d.bendEnd))
                    cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
                }
                lineTo(end.x, end.y)
            }
            drawPath(
                path,
                colour,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = if (line.edge.style == DiagramEdge.Style.DASHED) dashes else null,
                ),
            )
            // The head points along the last straight run: from the bend's end when there is one.
            val from = line.detour?.let { px(it.bendEnd) } ?: start
            val angle = atan2(end.y - from.y, end.x - from.x)
            val head = Path().apply {
                moveTo(end.x, end.y)
                lineTo(end.x - arrow * cos(angle - 0.4f), end.y - arrow * sin(angle - 0.4f))
                lineTo(end.x - arrow * cos(angle + 0.4f), end.y - arrow * sin(angle + 0.4f))
                close()
            }
            drawPath(head, colour)
        }
    }
    for (line in drawn) {
        val edge = line.edge
        val label = edge.label?.takeIf { it.isNotBlank() }
        val midX = line.label.x.dp
        val midY = line.label.y.dp
        val tap = onEdge?.let { Modifier.combinedClickableEdge { it(edge.id) } } ?: Modifier
        when {
            label != null -> Surface(
                modifier = Modifier.centredAt(midX, midY, 160.dp).then(tap),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
            // A 14 dp dot inside a 32 dp target, so a finger can hit it.
            onEdge != null -> Box(
                Modifier
                    .centredAt(midX, midY, 32.dp)
                    .size(32.dp)
                    .semantics { contentDescription = "Arrow ${edge.source} to ${edge.target}" }
                    .then(tap),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .border(1.5.dp, colour, CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableEdge(onClick: () -> Unit) =
    combinedClickable(onClickLabel = "Edit arrow", onClick = onClick)
