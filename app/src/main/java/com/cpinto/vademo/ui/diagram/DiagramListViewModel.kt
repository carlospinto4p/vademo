package com.cpinto.vademo.ui.diagram

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramFile
import com.cpinto.vademo.kb.createDiagram
import com.cpinto.vademo.kb.diagramsIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DiagramListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val kb = repository.knowledgeBase(kbName)

    /** Null until read. */
    private val _diagrams = MutableStateFlow<List<DiagramFile>?>(null)
    val diagrams: StateFlow<List<DiagramFile>?> = _diagrams

    /** False while a sync conflict waits in this knowledge base, as for editing notes. */
    private val _editable = MutableStateFlow(false)
    val editable: StateFlow<Boolean> = _editable

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _diagrams.value = diagramsIn(kb.root)
            _editable.value = !repository.needsChoice(kbName)
        }
    }

    /** Writes a new, empty diagram, then [onCreated] with its name so the screen can open it. */
    fun create(title: String, direction: Diagram.Direction, onCreated: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.needsChoice(kbName)) return@launch
            val name = runCatching { createDiagram(kb.root, title, direction) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { onCreated(name) }
        }
    }
}
