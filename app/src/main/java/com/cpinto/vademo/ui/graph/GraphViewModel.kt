package com.cpinto.vademo.ui.graph

import androidx.compose.foundation.layout.size
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
