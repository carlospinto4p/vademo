package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
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
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramEdge
import com.cpinto.vademo.kb.DiagramLayout
import com.cpinto.vademo.kb.DiagramNode
import com.cpinto.vademo.kb.DiagramPlaces
import com.cpinto.vademo.kb.EdgeLabels
import com.cpinto.vademo.kb.EdgeRoute
import com.cpinto.vademo.kb.Placed
import com.cpinto.vademo.kb.Position
import com.cpinto.vademo.kb.Ref
import com.cpinto.vademo.kb.Size
import com.cpinto.vademo.ui.graph.LocalZoom
import com.cpinto.vademo.ui.graph.Viewport
import com.cpinto.vademo.ui.theme.Palette
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
    /** Selected boxes, drawn outlined; dragging one moves them all. */
    val selected: Set<String> = emptySet(),
    /** A drag of a selected box, in dp; null when nothing can be dragged. */
    val onDrag: ((dx: Float, dy: Float) -> Unit)? = null,
    val onDragEnd: () -> Unit = {},
)

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
internal fun headline(node: DiagramNode, diagram: Diagram): String =
    listOfNotNull(
        node.tag,
        node.status?.name?.lowercase(),
        diagram.legend.firstOrNull { it.id == node.category }?.label?.takeIf { it != node.text },
    ).joinToString(" · ")

internal fun refText(ref: Ref): String = ref.line?.let { "${ref.path}:$it" } ?: ref.path

private const val LABEL_LINES = 3
private const val NOTES_LINES = 4

/**
 * Measures every box's text, then lays the diagram out around those sizes, with the boxes [moves] dragged.
 * The measuring is remembered apart from the placing, so a drag frame, which changes only [moves], places
 * the boxes again without measuring any text.
 */
@Composable
internal fun rememberPlaces(diagram: Diagram, moves: Map<String, Position> = emptyMap()): DiagramPlaces {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val styles = boxStyles()
    val sizes = remember(diagram, density, styles.label, styles.line, styles.notes) {
        with(density) {
            diagram.nodes.filterNot { it.isGroup }.associate { node ->
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
        }
    }
    return remember(diagram, sizes, moves) { DiagramLayout.place(diagram, sizes, moves) }
}

/**
 * Status and legend colours, readable on the light and the dark theme: each
 * comes from [Palette], which checks its contrast. A data class, so a box
 * whose colours did not change can skip recomposing.
 */
internal data class DiagramColours(val dark: Boolean, val error: Color, val outline: Color) {
    fun stripe(hue: Float) = Color(Palette.stripe(hue, dark))
    fun tint(hue: Float) = Color(Palette.tint(hue, dark))
    private val added = Color(Palette.status(135f, 0.6f, if (dark) 0.55f else 0.35f, dark, Diagram.LEGEND_HUES))
    private val changed = Color(Palette.status(40f, 0.9f, if (dark) 0.6f else 0.45f, dark, Diagram.LEGEND_HUES))
    fun status(status: DiagramNode.Status?): Color? = when (status) {
        DiagramNode.Status.ADDED -> added
        DiagramNode.Status.CHANGED -> changed
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
    // A drag changes only the places, so the groups' order and the boxes are worked out once per diagram.
    val (groups, boxes) = remember(diagram) {
        val byId = diagram.nodes.associateBy { it.id }
        val depth = { node: DiagramNode -> generateSequence(node) { it.parent?.let(byId::get) }.take(diagram.nodes.size + 1).count() }
        // Outer groups first, so a group inside another is drawn over it.
        diagram.nodes.filter { it.isGroup }.sortedBy(depth) to diagram.nodes.filterNot { it.isGroup }
    }
    Box(Modifier.fillMaxSize()) {
        groups.forEach { group ->
            places.boxes[group.id]?.let { GroupBox(group, diagram, it, colours, notes[group.id] != null, actions) }
        }
        Edges(diagram, places, actions?.onEdge)
        boxes.forEach { node ->
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
            val second = group.notes?.takeIf { it.isNotBlank() } ?: group.ref?.let(::refText)
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                // The layout leaves a group 48 dp above its boxes: room for two lines of title, or one and a note.
                maxLines = if (second == null) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (group.status == DiagramNode.Status.REMOVED) TextDecoration.LineThrough else null,
            )
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
    val middle: Position get() = detour?.label ?: Position((start.x + end.x) / 2, (start.y + end.y) / 2)
    val points: List<Position> get() = EdgeLabels.points(start, detour?.pieces ?: listOf(EdgeRoute.Piece.Line(end)))
}

private val LABEL_WIDTH = 160.dp
private val LABEL_PAD_X = 6.dp
private val LABEL_PAD_Y = 2.dp
private const val LABEL_MAX_LINES = 2

/**
 * The arrows, with their labels at their middles, or on the bend or lane of
 * an arrow that goes round a box ([EdgeRoute]); a label that would cover a box
 * or another label slides along its arrow ([EdgeLabels]). When [onEdge] is given
 * each arrow can be tapped there, on its label or, without one, on a small
 * handle, to edit it.
 */
@Composable
private fun Edges(diagram: Diagram, places: DiagramPlaces, onEdge: ((String) -> Unit)?) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    val drawn = remember(diagram, places) {
        diagram.edges.mapNotNull { edge ->
            val a = places.boxes[edge.source] ?: return@mapNotNull null
            val b = places.boxes[edge.target] ?: return@mapNotNull null
            if (edge.source == edge.target) return@mapNotNull null
            val detour = EdgeRoute.detour(a, b, diagram.direction, EdgeRoute.obstacles(diagram, places, edge.source, edge.target), places)
            DrawnEdge(edge, detour?.start ?: EdgeRoute.exit(a, b), detour?.end ?: EdgeRoute.exit(b, a), detour)
        }
    }
    val measurer = rememberTextMeasurer()
    val screen = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelMedium
    // Measured as drawn below: the text inside its padding (the outline is drawn over the pill's edge).
    val labelSizes = remember(diagram, screen, labelStyle) {
        with(screen) {
            val inner = Constraints(maxWidth = (LABEL_WIDTH - LABEL_PAD_X * 2).roundToPx())
            diagram.edges.associateWith { edge ->
                edge.label?.takeIf { it.isNotBlank() }?.let { text ->
                    val size = measurer.measure(text, labelStyle, overflow = TextOverflow.Ellipsis, maxLines = LABEL_MAX_LINES, constraints = inner).size
                    Size(
                        (size.width.toDp() + LABEL_PAD_X * 2).value.toDouble(),
                        (size.height.toDp() + LABEL_PAD_Y * 2).value.toDouble(),
                    )
                }
            }
        }
    }
    val labelPlaces = remember(drawn, labelSizes, places) {
        val boxes = diagram.nodes.filterNot { it.isGroup }.mapNotNull { places.boxes[it.id] }
        EdgeLabels.place(
            drawn.map { line ->
                labelSizes[line.edge]?.let { EdgeLabels.Label(line.points, line.middle, it.width, it.height) }
            },
            boxes,
        )
    }
    val zoom = LocalZoom.current
    Canvas(Modifier.fillMaxSize()) {
        val arrow = 10.dp.toPx()
        val width = Viewport.lineWidth(1.5.dp.toPx(), 1.dp.toPx(), zoom())
        val dashes = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        fun px(p: Position) = Offset(p.x.toFloat() * density, p.y.toFloat() * density)
        for (line in drawn) {
            val start = px(line.start)
            val end = px(line.end)
            val path = Path().apply {
                moveTo(start.x, start.y)
                val pieces = line.detour?.pieces ?: listOf(EdgeRoute.Piece.Line(line.end))
                for (piece in pieces) {
                    val to = px(piece.to)
                    when (piece) {
                        is EdgeRoute.Piece.Line -> lineTo(to.x, to.y)
                        is EdgeRoute.Piece.Curve -> {
                            val (c1, c2) = px(piece.control1) to px(piece.control2)
                            cubicTo(c1.x, c1.y, c2.x, c2.y, to.x, to.y)
                        }
                    }
                }
            }
            drawPath(
                path,
                colour,
                style = Stroke(
                    width = width,
                    pathEffect = if (line.edge.style == DiagramEdge.Style.DASHED) dashes else null,
                ),
            )
            // The head points along the last straight run into the target.
            val from = line.detour?.let { px(it.beforeEnd) } ?: start
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
    drawn.forEachIndexed { i, line ->
        val edge = line.edge
        val label = edge.label?.takeIf { it.isNotBlank() }
        val at = labelPlaces[i] ?: line.middle
        val tap = onEdge?.let { Modifier.combinedClickableEdge { it(edge.id) } } ?: Modifier
        when {
            label != null -> Surface(
                modifier = Modifier.centredAt(at.x.dp, at.y.dp, LABEL_WIDTH).then(tap),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    label,
                    style = labelStyle,
                    maxLines = LABEL_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = LABEL_PAD_X, vertical = LABEL_PAD_Y),
                )
            }
            // A 14 dp dot inside a 32 dp target, so a finger can hit it.
            onEdge != null -> Box(
                Modifier
                    .centredAt(at.x.dp, at.y.dp, 32.dp)
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
