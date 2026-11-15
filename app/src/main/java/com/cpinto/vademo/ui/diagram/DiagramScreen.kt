package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramEdge
import com.cpinto.vademo.kb.DiagramLayout
import com.cpinto.vademo.kb.DiagramNode
import com.cpinto.vademo.kb.DiagramPlaces
import com.cpinto.vademo.kb.Placed
import com.cpinto.vademo.kb.Ref
import com.cpinto.vademo.kb.readDiagram
import com.cpinto.vademo.ui.graph.Legend
import com.cpinto.vademo.ui.graph.PanZoom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val NODE_WIDTH = 200.dp
private val NODE_PADDING = 10.dp
private val STRIPE = 4.dp
private val GAP = 4.dp

data class DiagramState(
    val title: String = "",
    val diagram: Diagram? = null,
    val error: String? = null,
    /** Box id → path of the note its `ref` names, for the boxes whose ref is a note. */
    val notes: Map<String, String> = emptyMap(),
)

@HiltViewModel
class DiagramViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val name: String = checkNotNull(savedState["name"])
    private val kb = repository.knowledgeBase(kbName)

    private val _state = MutableStateFlow(DiagramState(title = name))
    val state: StateFlow<DiagramState> = _state

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = readDiagram(File(File(kb.root, Diagram.FOLDER), "$name.json"))
            val diagram = file.diagram
            val notes = diagram?.nodes.orEmpty().mapNotNull { node ->
                val note = node.ref?.let { kb.noteAt(it.path) } ?: return@mapNotNull null
                node.id to kb.relativePath(note)
            }.toMap()
            _state.value = DiagramState(file.title, diagram, file.error, notes)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagramScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    viewModel: DiagramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                diagram.nodes.isEmpty() -> Text("This diagram has no boxes yet.", Modifier.padding(16.dp))
                else -> {
                    val places = rememberPlaces(diagram)
                    val colours = diagramColours()
                    PanZoom(places.width.dp, places.height.dp) {
                        DiagramContent(diagram, places, colours, state.notes) { onOpenNote(viewModel.kbName, it) }
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

private fun refText(ref: Ref): String = ref.line?.let { "${ref.path}:$it" } ?: ref.path

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

@Composable
internal fun DiagramContent(
    diagram: Diagram,
    places: DiagramPlaces,
    colours: DiagramColours,
    notes: Map<String, String>,
    onOpen: (path: String) -> Unit,
) {
    val byId = diagram.nodes.associateBy { it.id }
    val depth = { node: DiagramNode -> generateSequence(node) { it.parent?.let(byId::get) }.take(diagram.nodes.size + 1).count() }
    Box(Modifier.fillMaxSize()) {
        // Outer groups first, so a group inside another is drawn over it.
        diagram.nodes.filter { it.isGroup }.sortedBy(depth).forEach { group ->
            places.boxes[group.id]?.let { GroupBox(group, diagram, it, colours, notes[group.id], onOpen) }
        }
        Edges(diagram.edges, places)
        diagram.nodes.filterNot { it.isGroup }.forEach { node ->
            places.boxes[node.id]?.let { NodeBox(node, diagram, it, colours, notes[node.id], onOpen) }
        }
    }
}

private fun Modifier.at(place: Placed) =
    offset(place.x.dp, place.y.dp).size(place.width.dp, place.height.dp)

@Composable
private fun NodeBox(
    node: DiagramNode,
    diagram: Diagram,
    place: Placed,
    colours: DiagramColours,
    note: String?,
    onOpen: (String) -> Unit,
) {
    val styles = boxStyles()
    val hue = diagram.hueOf(node.category)
    val shape = RoundedCornerShape(8.dp)
    val border = colours.status(node.status) ?: MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = Modifier
            .at(place)
            .border(if (node.status != null) 2.dp else 1.dp, border, shape)
            .then(if (note != null) Modifier.clickable { onOpen(note) } else Modifier),
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
                node.ref?.let { RefLine(it, note != null, styles.line) }
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
    note: String?,
    onOpen: (String) -> Unit,
) {
    val styles = boxStyles()
    val hue = diagram.hueOf(group.category)
    val shape = RoundedCornerShape(12.dp)
    val border = colours.status(group.status) ?: MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .at(place)
            .background(hue?.let(colours::tint)?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f), shape)
            .border(if (group.status != null) 2.dp else 1.dp, border, shape),
    ) {
        if (hue != null) {
            Box(Modifier.padding(vertical = 12.dp).width(STRIPE).fillMaxHeight().background(colours.stripe(hue)))
        }
        Column(
            Modifier
                .padding(horizontal = DiagramLayout.PAD_SIDE.dp, vertical = 6.dp)
                .then(if (note != null) Modifier.clickable { onOpen(note) } else Modifier),
        ) {
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
                    color = if (note != null && group.notes.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun Edges(edges: List<DiagramEdge>, places: DiagramPlaces) {
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
        val label = edge.label?.takeIf { it.isNotBlank() } ?: continue
        val midX = ((a.centreX + b.centreX) / 2).dp
        val midY = ((a.centreY + b.centreY) / 2).dp
        Surface(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = 160.dp.roundToPx()))
                layout(placeable.width, placeable.height) {
                    placeable.place(midX.roundToPx() - placeable.width / 2, midY.roundToPx() - placeable.height / 2)
                }
            },
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
    }
}
