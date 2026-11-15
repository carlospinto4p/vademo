package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import com.cpinto.vademo.kb.Placed
import com.cpinto.vademo.kb.Ref
import com.cpinto.vademo.ui.graph.Legend
import com.cpinto.vademo.ui.graph.PanZoom
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

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
    var menu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.editable && state.diagram != null) {
                        IconButton(onClick = viewModel::undo, enabled = state.canUndo) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }
                        IconButton(onClick = viewModel::redo, enabled = state.canRedo) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Add box") }, onClick = {
                                    menu = false
                                    viewModel.editReturning { it.addNode(group = false) }?.let { open = DiagramDialog.EditNode(it) }
                                })
                                DropdownMenuItem(text = { Text("Add group") }, onClick = {
                                    menu = false
                                    viewModel.editReturning { it.addNode(group = true) }?.let { open = DiagramDialog.EditNode(it) }
                                })
                                DropdownMenuItem(text = { Text("Legend") }, onClick = {
                                    menu = false
                                    open = DiagramDialog.Legend
                                })
                            }
                        }
                    }
                },
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
                    val places = rememberPlaces(diagram)
                    val colours = diagramColours()
                    val actions = DiagramActions(
                        onTap = { id -> viewModel.tap(id)?.let { onOpenNote(viewModel.kbName, it) } },
                        onLongPress = if (state.editable) { id -> open = DiagramDialog.Actions(id) } else null,
                        onEdge = if (state.editable) { id -> open = DiagramDialog.Edge(id) } else null,
                        highlighted = state.connectingFrom,
                    )
                    PanZoom(places.width.dp, places.height.dp) {
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

/** Measures every box's text, then lays the diagram out around those sizes. */
@Composable
internal fun rememberPlaces(diagram: Diagram): DiagramPlaces {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val styles = boxStyles()
    return remember(diagram, density, styles.label, styles.notes) {
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
            DiagramLayout.place(diagram, sizes)
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
        Edges(diagram.edges, places, actions?.onEdge)
        diagram.nodes.filterNot { it.isGroup }.forEach { node ->
            places.boxes[node.id]?.let { NodeBox(node, diagram, it, colours, notes[node.id] != null, actions) }
        }
    }
}

private fun Modifier.at(place: Placed) =
    offset(place.x.dp, place.y.dp).size(place.width.dp, place.height.dp)

/** Tap and long press on a box or group, when the drawing answers touches. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.touch(id: String, actions: DiagramActions?): Modifier =
    if (actions == null) this
    else combinedClickable(
        onLongClickLabel = if (actions.onLongPress != null) "Edit" else null,
        onLongClick = actions.onLongPress?.let { { it(id) } },
        onClick = { actions.onTap(id) },
    )

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
    val lit = actions?.highlighted == node.id
    val border = if (lit) MaterialTheme.colorScheme.primary else colours.status(node.status) ?: MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier
            .at(place)
            .border(if (lit) 3.dp else if (node.status != null) 2.dp else 1.dp, border, shape)
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
    val lit = actions?.highlighted == group.id
    val border = if (lit) MaterialTheme.colorScheme.primary else colours.status(group.status) ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .at(place)
            .background(hue?.let(colours::tint)?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), shape)
            .border(if (lit) 3.dp else if (group.status != null) 2.dp else 1.dp, border, shape)
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

/** Where the line from [from]'s centre towards [to] leaves [from]'s box. */
private fun exitPoint(from: Placed, to: Offset, density: Float): Offset {
    val cx = from.centreX.toFloat() * density
    val cy = from.centreY.toFloat() * density
    val dx = to.x - cx
    val dy = to.y - cy
    if (dx == 0f && dy == 0f) return Offset(cx, cy)
    val halfW = from.width.toFloat() * density / 2
    val halfH = from.height.toFloat() * density / 2
    val t = minOf(if (dx != 0f) halfW / abs(dx) else Float.MAX_VALUE, if (dy != 0f) halfH / abs(dy) else Float.MAX_VALUE)
    return Offset(cx + dx * t, cy + dy * t)
}

/** Places a child centred on ([x], [y]) inside the drawing. */
private fun Modifier.centredAt(x: Dp, y: Dp, maxWidth: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth.roundToPx()))
    layout(placeable.width, placeable.height) {
        placeable.place(x.roundToPx() - placeable.width / 2, y.roundToPx() - placeable.height / 2)
    }
}

/**
 * The arrows, with their labels at their middles. When [onEdge] is given each
 * arrow can be tapped at its middle, on its label or, without one, on a small
 * handle, to edit it.
 */
@Composable
private fun Edges(edges: List<DiagramEdge>, places: DiagramPlaces, onEdge: ((String) -> Unit)?) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    val drawn = edges.mapNotNull { edge ->
        val a = places.boxes[edge.source] ?: return@mapNotNull null
        val b = places.boxes[edge.target] ?: return@mapNotNull null
        if (edge.source == edge.target) null else Triple(edge, a, b)
    }
    Canvas(Modifier.fillMaxSize()) {
        val arrow = 10.dp.toPx()
        val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        for ((edge, a, b) in drawn) {
            val start = exitPoint(a, Offset(b.centreX.toFloat() * density, b.centreY.toFloat() * density), density)
            val end = exitPoint(b, Offset(a.centreX.toFloat() * density, a.centreY.toFloat() * density), density)
            drawLine(
                colour,
                start,
                end,
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = if (edge.style == DiagramEdge.Style.DASHED) dashes else null,
            )
            val angle = atan2(end.y - start.y, end.x - start.x)
            val head = Path().apply {
                moveTo(end.x, end.y)
                lineTo(end.x - arrow * cos(angle - 0.4f), end.y - arrow * sin(angle - 0.4f))
                lineTo(end.x - arrow * cos(angle + 0.4f), end.y - arrow * sin(angle + 0.4f))
                close()
            }
            drawPath(head, colour)
        }
    }
    for ((edge, a, b) in drawn) {
        val label = edge.label?.takeIf { it.isNotBlank() }
        val midX = ((a.centreX + b.centreX) / 2).dp
        val midY = ((a.centreY + b.centreY) / 2).dp
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
