package com.cpinto.vademo.ui.graph

import com.cpinto.vademo.ui.BackForward

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.GraphLayout
import com.cpinto.vademo.kb.Layout
import com.cpinto.vademo.kb.NoteGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One layout unit (the preferred gap between linked notes) on screen. */
private val UNIT = 180.dp
private val BOX_WIDTH = 140.dp
private val BOX_HEIGHT = 52.dp
/** Space kept clear around each box, so neighbours never touch. */
private val BOX_MARGIN = 12.dp

data class GraphState(
    val graph: NoteGraph? = null,
    val layout: Layout? = null,
    /** Top-level folders in the order their colours are assigned. */
    val folders: List<String> = emptyList(),
)

@HiltViewModel
class GraphViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])

    private val _state = MutableStateFlow(GraphState())
    val state: StateFlow<GraphState> = _state

    /** [aspect] is the screen's height ÷ width, so the map is laid out in that shape. */
    fun load(aspect: Double) {
        if (_state.value.graph != null) return
        viewModelScope.launch(Dispatchers.Default) {
            val graph = NoteGraph.of(repository.index(kbName))
            val layout = GraphLayout.layout(
                count = graph.notes.size,
                links = graph.links,
                groups = graph.notes.map { it.folder },
                boxWidth = ((BOX_WIDTH + BOX_MARGIN) / UNIT).toDouble(),
                boxHeight = ((BOX_HEIGHT + BOX_MARGIN) / UNIT).toDouble(),
                aspect = aspect,
            )
            _state.value = GraphState(graph, layout, graph.notes.map { it.folder }.distinct().sorted())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    onBack: () -> Unit,
    onOpenNote: (kb: String, path: String) -> Unit,
    viewModel: GraphViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val window = LocalWindowInfo.current.containerSize
    LaunchedEffect(Unit) { viewModel.load(window.height.toDouble() / window.width.coerceAtLeast(1)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map of notes") },
                navigationIcon = { BackForward(onBack) },
            )
        },
    ) { padding ->
        val graph = state.graph
        val layout = state.layout
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                graph == null || layout == null -> Unit
                graph.notes.isEmpty() -> Text("This knowledge base has no notes yet.", Modifier.padding(16.dp))
                else -> {
                    val colours = folderColours(state.folders)
                    PanZoom(contentWidth = UNIT * layout.width.toFloat(), contentHeight = UNIT * layout.height.toFloat()) {
                        GraphContent(graph, layout, colours) { onOpenNote(viewModel.kbName, it) }
                    }
                    Legend(
                        state.folders.map { it.ifEmpty { "index" } to colours.getValue(it) },
                        Modifier.align(Alignment.BottomStart).padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphContent(
    graph: NoteGraph,
    layout: Layout,
    colours: Map<String, Color>,
    onOpen: (path: String) -> Unit,
) {
    val lineColour = MaterialTheme.colorScheme.outline
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = UNIT.toPx()
            for ((a, b) in graph.links) {
                val p = layout.centres[a]
                val q = layout.centres[b]
                drawLine(
                    color = lineColour,
                    start = Offset((p.x * unit).toFloat(), (p.y * unit).toFloat()),
                    end = Offset((q.x * unit).toFloat(), (q.y * unit).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
        graph.notes.forEachIndexed { i, note ->
            val centre = layout.centres[i]
            val colour = colours.getValue(note.folder)
            Surface(
                modifier = Modifier
                    .offset(
                        x = UNIT * centre.x.toFloat() - BOX_WIDTH / 2,
                        y = UNIT * centre.y.toFloat() - BOX_HEIGHT / 2,
                    )
                    .size(BOX_WIDTH, BOX_HEIGHT)
                    .border(2.dp, colour, RoundedCornerShape(8.dp))
                    .clickable { onOpen(note.path) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        note.title,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One colour per folder, spread around the colour wheel in folder-name order. */
private fun folderColours(folders: List<String>): Map<String, Color> =
    folders.withIndex().associate { (i, folder) ->
        folder to Color.hsv(hue = (i * 360f / folders.size.coerceAtLeast(1) + 200f) % 360f, saturation = 0.55f, value = 0.8f)
    }
