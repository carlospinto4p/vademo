package com.cpinto.vademo.ui.browse

import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.kb.Folder
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.NoteSummary
import com.cpinto.vademo.kb.NoteTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class BrowseState(
    val title: String = "",
    val folders: List<Folder> = emptyList(),
    val notes: List<NoteSummary> = emptyList(),
    /** At the top of a knowledge base only, for the Recent, Types and Tags tabs; null while it is read. */
    val index: NoteIndex? = null,
    /** Names of the knowledge base's note templates, offered by New note inside a folder. */
    val templates: List<String> = emptyList(),
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    private val path: String = savedState["path"] ?: ""
    val isTop: Boolean = path.isEmpty()
    private val kb = repository.knowledgeBase(kbName)

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            // Reopened at the next launch, unless the home screen is gone back to first.
            if (isTop) settings.saveLastKb(kbName)
            val dir = if (path.isEmpty()) kb.root else kb.fileAt(path)
            // At the root only the index is a note; README, CLAUDE.md and the
            // backlog are repository files, not knowledge.
            val notes = if (path.isEmpty()) listOfNotNull(kb.index?.let(kb::summary)) else kb.notesIn(dir)
            _state.value = BrowseState(
                title = if (path.isEmpty()) kbName else path,
                folders = kb.foldersIn(dir),
                notes = notes,
                templates = if (path.isEmpty()) emptyList() else kb.templates().map { it.nameWithoutExtension },
            )
            // Read after the folders are on screen, since it may open every note.
            if (isTop) _state.value = _state.value.copy(index = repository.index(kbName))
        }
    }

    fun pathOf(folder: Folder): String = kb.relativePath(folder.dir)

    fun pathOf(note: NoteSummary): String = kb.relativePath(note.file)

    /**
     * Checks [name] for a new note in this folder, then hands [onReady] its
     * path and its template's path, or [onError] the reason it cannot be used.
     * Names are unique across the whole knowledge base, since `[[name]]`
     * must lead to one note.
     */
    fun startNote(template: String, name: String, onError: (String) -> Unit, onReady: (path: String, template: String) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                if (!NoteTemplate.isValidName(name)) {
                    "Use lowercase letters, digits and dashes for the file name."
                } else {
                    kb.resolve(name)?.let { "There is already a note named $name: ${kb.relativePath(it)}" }
                }
            }
            if (error != null) onError(error) else onReady("$path/$name.md", "${NoteTemplate.FOLDER}/$template.md")
        }
    }
}
