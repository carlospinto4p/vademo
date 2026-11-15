package com.cpinto.vademo.ui.diagram

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.DiagramClipboard
import com.cpinto.vademo.data.DiagramMoves
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramDocument
import com.cpinto.vademo.kb.Position
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    /** Boxes picked for an action on several at once; dragging one of them moves them all. */
    val selected: Set<String> = emptySet(),
    /** Where boxes were dragged on this phone, as offsets from the layout. Never in the file. */
    val moves: Map<String, Position> = emptyMap(),
    val canPaste: Boolean = false,
    /** Set once the diagram's file has been deleted, so the screen can close. */
    val deleted: Boolean = false,
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
    private val placesStore: DiagramMoves,
    private val clipboard: DiagramClipboard,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    /** The diagram's file name, without `.json`. */
    val name: String = checkNotNull(savedState["name"])
    private val kb = repository.knowledgeBase(kbName)
    /** Null when [name] is not a plain file name; then nothing is read or written. */
    private val file = kb.diagramFile(name)

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
        if (file == null) {
            _state.value = DiagramState(title = name, error = "\"$name\" is not a diagram name.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val moves = placesStore.read(kbName, name)
            _state.update { it.copy(moves = moves, canPaste = clipboard.clip != null) }
            read(message = null)
        }
    }

    private suspend fun read(message: String?) {
        val file = file ?: return
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

    fun startConnecting(from: String) = _state.update { it.copy(connectingFrom = from, selected = emptySet()) }

    fun cancelConnecting() = _state.update { it.copy(connectingFrom = null) }

    /**
     * A tap on a box: while an arrow is being drawn it ends the arrow there;
     * while boxes are selected it adds the box to the selection or takes it
     * out; otherwise the box's note opens when it links to one. Returns the
     * note's path to open, if any.
     */
    fun tap(id: String): String? {
        val now = _state.value
        val from = now.connectingFrom
        if (from != null) {
            _state.update { it.copy(connectingFrom = null) }
            if (from != id) editReturning { it.addEdge(from, id) }
            return null
        }
        if (now.selected.isNotEmpty()) {
            _state.update { it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id) }
            return null
        }
        return now.notes[id]
    }

    // ---- selection --------------------------------------------------------

    fun select(id: String) = _state.update { it.copy(selected = setOf(id), connectingFrom = null) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    /** Applies [change] to the selected boxes as one undo step, then ends the selection. */
    fun editSelected(change: (DiagramDocument, Set<String>) -> DiagramDocument) {
        val ids = _state.value.selected
        if (ids.isEmpty()) return
        edit { change(it, ids) }
        clearSelection()
    }

    // ---- dragging (places on this phone only) -----------------------------

    /**
     * Moves the selected boxes by ([dx], [dy]) dp while a finger drags one of
     * them. A box inside a selected group is moved by its group, not twice.
     */
    fun drag(dx: Float, dy: Float) {
        val diagram = _state.value.diagram ?: return
        val parentOf = diagram.nodes.associate { it.id to it.parent }
        val selected = _state.value.selected
        val outer = selected.filter { id ->
            generateSequence(parentOf[id]) { parentOf[it] }.take(diagram.nodes.size).none { it in selected }
        }
        _state.update { s ->
            val moves = s.moves.toMutableMap()
            for (id in outer) {
                val was = moves[id] ?: Position(0.0, 0.0)
                moves[id] = Position(was.x + dx, was.y + dy)
            }
            s.copy(moves = moves)
        }
    }

    fun dragEnded() = saveMoves()

    /** tyfold's Tidy: forgets every place dragged on this phone, so the layout places everything again. */
    fun tidy() {
        _state.update { it.copy(moves = emptyMap()) }
        saveMoves()
    }

    private fun saveMoves() {
        if (file == null) return
        val moves = _state.value.moves
        viewModelScope.launch(Dispatchers.IO) { placesStore.write(kbName, name, moves) }
    }

    // ---- cut, copy, paste, duplicate --------------------------------------

    fun copy(ids: Set<String>) {
        val clip = doc?.copy(ids) ?: return
        clipboard.clip = clip
        _state.update { it.copy(canPaste = true, selected = emptySet(), message = copied(clip.size)) }
    }

    fun cut(ids: Set<String>) {
        var clip: com.cpinto.vademo.kb.DiagramClip? = null
        edit { d -> d.cut(ids)?.also { clip = it.second }?.first ?: d }
        clip?.let { c ->
            clipboard.clip = c
            _state.update { it.copy(canPaste = true, selected = emptySet(), message = copied(c.size)) }
        }
    }

    /** Pastes what was cut or copied, here or in another diagram; the pasted boxes come back selected. */
    fun paste() {
        val clip = clipboard.clip ?: return
        val pasted = editReturning { it.paste(clip) } ?: return
        _state.update { it.copy(selected = pasted) }
    }

    fun duplicate(ids: Set<String>) {
        val copies = editReturning { it.duplicate(ids) } ?: return
        _state.update { it.copy(selected = copies) }
    }

    private fun copied(count: Int) = if (count == 1) "1 box copied" else "$count boxes copied"

    // ---- the diagram as a file --------------------------------------------

    /** Deletes the diagram's file and what this phone kept about it; the screen then closes. */
    fun deleteDiagram() {
        val file = file ?: return
        if (!_state.value.editable) return
        pendingSave?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            file.delete()
            placesStore.forget(kbName, name)
            doc = null
            _state.update { it.copy(deleted = true) }
        }
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
        val file = file ?: return@withContext
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
