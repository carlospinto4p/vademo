package com.cpinto.vademo.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.kb.FoldedMap
import com.cpinto.vademo.kb.Layout
import com.cpinto.vademo.kb.MapBox
import com.cpinto.vademo.ui.BackForward
import com.cpinto.vademo.ui.BoxBounds
import com.cpinto.vademo.ui.LocalNoteOpenings
import com.cpinto.vademo.ui.rememberBoxBounds
import com.cpinto.vademo.ui.theme.Palette

/** One layout unit (the preferred gap between linked notes) on screen. */
internal val UNIT = 180.dp
internal val BOX_WIDTH = 140.dp
internal val BOX_HEIGHT = 52.dp
/** The index, topic hubs and folded topics: the boxes that show the knowledge base's shape. */
internal val LARGE_WIDTH = 176.dp
internal val LARGE_HEIGHT = 72.dp
/** The folder-coloured band across the top of a box. */
private val BAND = 6.dp
private val BAND_LARGE = 8.dp
/** Space kept clear around each box, so neighbours never touch. Also used by [GraphViewModel]'s layout. */
internal val BOX_MARGIN = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    /** Shown as a Search action when given, as on a knowledge base's Map tab. */
    onSearch: (() -> Unit)? = null,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openings = LocalNoteOpenings.current
    val bounds = rememberBoxBounds()
    val window = LocalWindowInfo.current.containerSize
    LaunchedEffect(Unit) { viewModel.load(window.height.toDouble() / window.width.coerceAtLeast(1)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val around = state.focusTitle
                    Text(if (around != null) "Around $around" else "Map of notes", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { BackForward(onBack) },
                actions = {
                    if (onSearch != null) {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val graph = state.graph
        val map = state.map
        val layout = state.layout
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (graph != null && graph.notes.isNotEmpty() && state.focus == null) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.allOpen,
                        onClick = viewModel::toggleAll,
                        label = { Text("Every note") },
                    )
                    FilterChip(
                        selected = state.sources,
                        onClick = viewModel::toggleSources,
                        label = { Text("Sources") },
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    // Reading every note and laying them out can take a moment on a big knowledge base.
                    graph == null || map == null || layout == null -> Loading()
                    graph.notes.isEmpty() -> Text(
                        if (state.focus != null) "This note is not in the knowledge base's index." else "This knowledge base has no notes yet.",
                        Modifier.padding(16.dp),
                    )
                    else -> {
                        val colours = folderColours(state.folders, MaterialTheme.colorScheme.surface.luminance() < 0.5f)
                        // Opened afresh each time a topic opens or folds, at a size that fits the new picture.
                        val focused = map.boxes.indexOfFirst { it.path == state.focus && state.focus != null }
                        // The note just read, back from it, or else the note the map is round.
                        val marked = map.boxes.indexOfFirst { !it.folded && it.path == state.lastOpened && state.lastOpened != null }
                            .takeIf { it >= 0 } ?: focused
                        key(map) {
                            PanZoom(
                                contentWidth = UNIT * layout.width.toFloat(),
                                contentHeight = UNIT * layout.height.toFloat(),
                                focus = layout.centres.getOrNull(focused)?.let { UNIT * it.x.toFloat() to UNIT * it.y.toFloat() },
                            ) {
                                GraphContent(
                                    map,
                                    layout,
                                    colours,
                                    marked = marked,
                                    bounds = bounds,
                                    onTap = { box ->
                                        viewModel.tap(box)?.let {
                                            // The note grows out of the box tapped.
                                            openings.from(bounds.of(box))
                                            onOpenNote(viewModel.kbName, it)
                                        }
                                    },
                                    onLongPress = viewModel::fold,
                                )
                            }
                        }
                        Legend(
                            map.boxes.map { it.folder }.distinct().sorted().map { it.ifEmpty { "index" } to colours.getValue(it) },
                            Modifier.align(Alignment.BottomStart).padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GraphContent(
    map: FoldedMap,
    layout: Layout,
    colours: Map<String, Color>,
    /**
     * The box drawn in the primary colour with its links, so where it leads shows at a glance: the note
     * last opened from the map, or the one the map is centred on; -1 for none.
     */
    marked: Int,
    /** Where each box is drawn, for its note to grow out of. */
    bounds: BoxBounds,
    onTap: (MapBox) -> Unit,
    onLongPress: (MapBox) -> Unit,
) {
    val lineColour = MaterialTheme.colorScheme.outline
    val markColour = MaterialTheme.colorScheme.primary
    val zoom = LocalZoom.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = UNIT.toPx()
            val width = Viewport.lineWidth(1.5.dp.toPx(), 1.dp.toPx(), zoom())
            fun line(a: Int, b: Int, colour: Color, strokeWidth: Float) {
                val p = layout.centres[a]
                val q = layout.centres[b]
                drawLine(
                    color = colour,
                    start = Offset((p.x * unit).toFloat(), (p.y * unit).toFloat()),
                    end = Offset((q.x * unit).toFloat(), (q.y * unit).toFloat()),
                    strokeWidth = strokeWidth,
                )
            }
            // The marked box's links last, so they are drawn over the others.
            val (own, others) = map.links.partition { (a, b) -> a == marked || b == marked }
            for ((a, b) in others) line(a, b, lineColour, width)
            for ((a, b) in own) line(a, b, markColour, width * 2)
        }
        map.boxes.forEachIndexed { i, box ->
            val centre = layout.centres[i]
            val folderColour = colours.getValue(box.folder)
            val colour = if (i == marked) markColour else folderColour
            val width = if (box.large) LARGE_WIDTH else BOX_WIDTH
            val height = if (box.large) LARGE_HEIGHT else BOX_HEIGHT
            val shape = RoundedCornerShape(if (box.large) 12.dp else 8.dp)
            Surface(
                modifier = Modifier
                    .offset(x = UNIT * centre.x.toFloat() - width / 2, y = UNIT * centre.y.toFloat() - height / 2)
                    .size(width, height)
                    .then(bounds.track(box))
                    .border(if (box.large || i == marked) 3.dp else 2.dp, colour, shape)
                    .combinedClickable(
                        onClickLabel = if (box.folded) "Show its notes" else "Open",
                        onLongClickLabel = if (box.folder.isNotEmpty() && !box.folded) "Fold the topic" else null,
                        onLongClick = if (box.folder.isNotEmpty() && !box.folded) ({ onLongPress(box) }) else null,
                        onClick = { onTap(box) },
                    ),
                shape = shape,
                color = if (box.large) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    // The folder's colour as a band across the top, a larger patch of it than the border alone.
                    Box(Modifier.fillMaxWidth().height(if (box.large) BAND_LARGE else BAND).background(folderColour))
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            box.title,
                            style = if (box.large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (box.folded) {
                            Text(
                                if (box.notes == 1) "1 note" else "${box.notes} notes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One colour per folder, spread around the colour wheel in folder-name
 * order, each dark or light enough to stand out from the boxes ([Palette]).
 */
private fun folderColours(folders: List<String>, dark: Boolean): Map<String, Color> =
    folders.withIndex().associate { (i, folder) ->
        folder to Color(Palette.folder((i * 360f / folders.size.coerceAtLeast(1) + 200f) % 360f, dark))
    }
