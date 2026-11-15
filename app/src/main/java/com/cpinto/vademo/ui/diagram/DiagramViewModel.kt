package com.cpinto.vademo.ui.diagram

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class DiagramState(
    val title: String = "",
    val diagram: Diagram? = null,
    val error: String? = null,
    /** Box id → path of the note its `ref` names, for the boxes whose ref is a note. */
    val notes: Map<String, String> = emptyMap(),
    /** False while the knowledge base waits for a sync conflict to be resolved. */
    val editable: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** Why the current diagram is not being saved, in tyfold's words; empty while it saves normally. */
    val problems: List<String> = emptyList(),
    /** The box an arrow is being drawn from, waiting for a tap on the box it goes to. */
    val connectingFrom: String? = null,
    /** A one-off message for the snackbar. */
    val message: String? = null,
)

/**
 * A diagram on its own screen, and its editing. Each edit is a new
 * [DiagramDocument]; the old ones are the undo history, which starts over
 * whenever the file is read again, as tyfold's does (undoing past a change
 * made elsewhere would write the older diagram over it).
 *
 * An edit is written to the file 400 ms after the last one, as tyfold writes
 * its canvas. Nothing is written over a file that changed on disk since it was
 * read: a sync may have brought a new version, so the file is read again and
 * the screen says the edit was not saved. Nothing is written while the
 * knowledge base waits for a conflict to be resolved, as with notes.
 */
@HiltViewModel
class DiagramViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val name: String = checkNotNull(savedState["name"])
    private val kb = repository.knowledgeBase(kbName)
    private val file = File(File(kb.root, Diagram.FOLDER), "$name.json")

    private val _state = MutableStateFlow(DiagramState(title = name))
    val state: StateFlow<DiagramState> = _state

    private var doc: DiagramDocument? = null
    private val undo = ArrayDeque<DiagramDocument>()
    private val redo = ArrayDeque<DiagramDocument>()
    /** The file's text when last read or written here; different text on disk means something else wrote it. */
    private var seen: String? = null
    private var pendingSave: Job? = null

    /** The document being edited, for the editors to read fields from. */
    val document: DiagramDocument? get() = doc

    fun load() {
        viewModelScope.launch(Dispatchers.IO) { read(message = null) }
    }

    private suspend fun read(message: String?) {
        val editable = repository.list().none { it.name == kbName && it.needsChoice }
        val text = runCatching { file.readText() }.getOrNull()
        val loaded = runCatching { DiagramDocument.parse(text!!) }
        seen = text
        undo.clear()
        redo.clear()
        loaded.onSuccess { doc = it; publish(it, editable, message) }
            .onFailure { e ->
                doc = null
                _state.value = DiagramState(title = name, error = e.message ?: e.javaClass.simpleName, message = message)
            }
    }

    private fun publish(document: DiagramDocument, editable: Boolean = _state.value.editable, message: String? = null) {
        val diagram = document.diagram
        val notes = diagram.nodes.mapNotNull { node ->
            val note = node.ref?.let { kb.noteAt(it.path) } ?: return@mapNotNull null
            node.id to kb.relativePath(note)
        }.toMap()
        _state.update {
            it.copy(
                title = diagram.title.ifBlank { name },
                diagram = diagram,
                error = null,
                notes = notes,
                editable = editable,
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
                problems = document.problems(),
                message = message ?: it.message,
            )
        }
    }

    /** Applies one edit as one undo step, and saves it shortly after. */
    fun edit(change: (DiagramDocument) -> DiagramDocument) {
        val current = doc ?: return
        if (!_state.value.editable) return
        val next = change(current)
        if (next == current) return
        undo.addLast(current)
        if (undo.size > HISTORY) undo.removeFirst()
        redo.clear()
        doc = next
        publish(next)
        scheduleSave()
    }

    /** Like [edit] for an edit that names something new, such as a new box; returns its id. */
    fun <T> editReturning(change: (DiagramDocument) -> Pair<DiagramDocument, T>?): T? {
        var result: T? = null
        edit { current -> change(current)?.also { result = it.second }?.first ?: current }
        return result
    }

    fun undo() = step(from = undo, to = redo)

    fun redo() = step(from = redo, to = undo)

    private fun step(from: ArrayDeque<DiagramDocument>, to: ArrayDeque<DiagramDocument>) {
        val current = doc ?: return
        val target = from.removeLastOrNull() ?: return
        to.addLast(current)
        doc = target
        publish(target)
        scheduleSave()
    }

    fun startConnecting(from: String) = _state.update { it.copy(connectingFrom = from) }

    fun cancelConnecting() = _state.update { it.copy(connectingFrom = null) }

    /**
     * A tap on a box: while an arrow is being drawn it ends the arrow there;
     * otherwise the box's note opens when it links to one. Returns the note's
     * path to open, if any.
     */
    fun tap(id: String): String? {
        val from = _state.value.connectingFrom
        if (from != null) {
            _state.update { it.copy(connectingFrom = null) }
            if (from != id) editReturning { it.addEdge(from, id) }
            return null
        }
        return _state.value.notes[id]
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        val document = doc ?: return@withContext
        if (document.problems().isNotEmpty()) return@withContext
        // Checked again here, not only on opening: a sync may have stopped on a conflict since.
        // The file is read again, so the screen shows what is on disk and not the edit that was refused.
        if (repository.list().any { it.name == kbName && it.needsChoice }) {
            read(message = "Not saved: this knowledge base has a sync conflict to resolve first.")
            return@withContext
        }
        val onDisk = runCatching { file.readText() }.getOrNull()
        if (onDisk != seen) {
            read(message = "The diagram changed on disk, so your last edit was not saved. It now shows the new version.")
            return@withContext
        }
        val text = document.toText()
        if (onDisk == text) return@withContext
        file.writeText(text)
        seen = text
    }

    override fun onCleared() {
        // A pending edit is written now rather than lost with the screen.
        if (pendingSave?.isActive == true) {
            pendingSave?.cancel()
            kotlinx.coroutines.runBlocking { save() }
        }
    }

    private companion object {
        const val HISTORY = 100
        const val SAVE_DELAY_MS = 400L
    }
}
