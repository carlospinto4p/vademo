package com.cpinto.vademo.ui.graph

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.FoldedMap
import com.cpinto.vademo.kb.GraphLayout
import com.cpinto.vademo.kb.Layout
import com.cpinto.vademo.kb.MapBox
import com.cpinto.vademo.kb.NoteGraph
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GraphState(
    val graph: NoteGraph? = null,
    /** What is drawn: [graph] with the folders not in [open] folded into one box each. */
    val map: FoldedMap? = null,
    val layout: Layout? = null,
    /** Top-level folders in the order their colours are assigned. */
    val folders: List<String> = emptyList(),
    /** Folders drawn note by note. */
    val open: Set<String> = emptySet(),
    /** Whether source notes are drawn. */
    val sources: Boolean = false,
    /** The note a map round one note is centred on, its path; null for the whole knowledge base. */
    val focus: String? = null,
    /**
     * The note selected, its path: drawn marked with its links, the notes it does not link to faded. A tap selects a
     * note and a second tap opens it, so it is also where reading left off. Round one note it starts as that note.
     */
    val selected: String? = null,
    /** How many notes besides its hub each open folder draws, where more than [FoldedMap.LIMIT]. */
    val limits: Map<String, Int> = emptyMap(),
) {
    /** The focused note's title, once read. */
    val focusTitle: String? get() = focus?.let { path -> graph?.notes?.firstOrNull { it.path == path }?.title }

    /** Topic folders that can be opened, sources left out while they are hidden. */
    val foldable: Set<String>
        get() = graph?.notes.orEmpty().filter { sources || !it.isSource }.map { it.folder }.filter { it.isNotEmpty() }.toSet()

    val allOpen: Boolean get() = foldable.isNotEmpty() && open.containsAll(foldable)
}

/**
 * The map of notes. Opened with a `focus` note, it is only that note and the
 * notes up to two links from it, every topic open. Otherwise it starts folded, as the knowledge base's shape: the
 * index and one box per topic, sources hidden. Tapping a folded topic opens
 * it in place, showing its most linked notes ([FoldedMap.LIMIT]) and a box
 * for the rest, which a tap shows more of. Tapping a note, or the hub of an
 * open topic, selects it to show its links, and tapping it again opens it.
 */
@HiltViewModel
class GraphViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val focus: String? = savedState.get<String>("focus")?.ifEmpty { null }

    private val _state = MutableStateFlow(GraphState(focus = focus, selected = focus))
    val state: StateFlow<GraphState> = _state

    private var aspect = 1.0
    private var placing: Job? = null

    /** [aspect] is the screen's height ÷ width, so the map is laid out in that shape. */
    fun load(aspect: Double) {
        if (_state.value.graph != null) return
        this.aspect = aspect
        viewModelScope.launch(Dispatchers.Default) {
            val whole = NoteGraph.of(repository.index(kbName))
            val graph = focus?.let { whole.around(it) } ?: whole
            // Colours follow the whole knowledge base's folders, so a folder looks the same on both maps.
            val folders = whole.notes.map { n -> n.folder }.distinct().sorted()
            _state.update {
                if (focus == null) it.copy(graph = graph, folders = folders)
                // Round one note nothing is folded or hidden: it is the neighbourhood as it is.
                else it.copy(graph = graph, folders = folders, open = folders.toSet(), sources = true)
            }
            place()
        }
    }

    /**
     * The note to open for a tap on [box]: the note already selected. Otherwise null, the tap having selected a
     * note, opened a folded topic or shown more of an open one.
     */
    fun tap(box: MapBox): String? {
        val state = _state.value
        when {
            box.folded -> show(state.open + box.folder, state.sources, state.limits)
            box.rest -> show(state.open, state.sources, state.limits + (box.folder to limitOf(box.folder) + FoldedMap.LIMIT))
            box.path == state.selected -> return box.path
            else -> _state.update { it.copy(selected = box.path) }
        }
        return null
    }

    /** Selects no note, so only the links that show the knowledge base's shape are drawn. */
    fun unselect() {
        _state.update { it.copy(selected = null) }
    }

    /** Folds the topic [box] is in back into one box. */
    fun fold(box: MapBox) {
        val state = _state.value
        if (box.folder.isNotEmpty()) show(state.open - box.folder, state.sources, state.limits - box.folder)
    }

    /** Opens every topic, or folds them all when they are all open. */
    fun toggleAll() {
        val state = _state.value
        if (state.allOpen) show(emptySet(), state.sources, emptyMap()) else show(state.foldable, state.sources, state.limits)
    }

    fun toggleSources() {
        val state = _state.value
        show(state.open, !state.sources, state.limits)
    }

    private fun limitOf(folder: String): Int = _state.value.limits[folder] ?: FoldedMap.LIMIT

    private fun show(open: Set<String>, sources: Boolean, limits: Map<String, Int>) {
        _state.update { it.copy(open = open, sources = sources, limits = limits) }
        placing?.cancel()
        placing = viewModelScope.launch(Dispatchers.Default) { place() }
    }

    private fun place() {
        val state = _state.value
        val graph = state.graph ?: return
        val map = FoldedMap.of(graph, state.open, state.sources, state.limits, keep = state.focus)
        // Every box is spaced as a large one, so a topic box never overlaps its neighbours.
        val layout = GraphLayout.layout(
            count = map.boxes.size,
            links = map.links,
            groups = map.boxes.map { it.folder },
            boxWidth = ((LARGE_WIDTH + BOX_MARGIN) / UNIT).toDouble(),
            boxHeight = ((LARGE_HEIGHT + BOX_MARGIN) / UNIT).toDouble(),
            aspect = aspect,
        )
        _state.update {
            if (it.open == state.open && it.sources == state.sources && it.limits == state.limits) it.copy(map = map, layout = layout) else it
        }
    }
}
