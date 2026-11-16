package com.cpinto.vademo.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.FrontmatterParser
import com.cpinto.vademo.kb.NoteTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

data class EditState(
    val loaded: Boolean = false,
    val original: String = "",
    val text: String = "",
    val blocked: Boolean = false,
    /** Why the note could not be read or saved; the text being edited stays on screen. */
    val error: String? = null,
) {
    val changed: Boolean get() = text != original
}

@HiltViewModel
class EditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    private val kbName: String = checkNotNull(savedState["kb"])
    private val path: String = checkNotNull(savedState["path"])
    private val kb = repository.knowledgeBase(kbName)
    private val file = kb.fileAt(path)

    /** Set when this is a new note started from a template: the template's path and the note's title. */
    private val template: String? = savedState.get<String>("template")?.takeIf { it.isNotEmpty() }
    private val title: String = savedState["title"] ?: ""
    val isNew: Boolean get() = template != null

    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state

    fun load() {
        if (_state.value.loaded) return
        viewModelScope.launch {
            // Editing during an unfinished merge would write into a file git
            // is still waiting on a choice for.
            val blocked = repository.needsChoice(kbName)
            // A new note is written only when saved, so leaving the editor leaves nothing behind.
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    if (template != null && !file.exists()) {
                        "" to NoteTemplate.fill(kb.fileAt(template).readText(), title, kb.hubOf(file.parentFile!!), LocalDate.now())
                    } else {
                        check(file.isFile) { "This note no longer exists. A sync may have removed it." }
                        file.readText().let { it to it }
                    }
                }
            }
            _state.value = read.fold(
                onSuccess = { (original, text) -> EditState(loaded = true, original = original, text = text, blocked = blocked) },
                onFailure = { EditState(error = "Could not open this note. ${it.message}") },
            )
        }
    }

    fun onTextChange(text: String) = _state.update { it.copy(text = text) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (!current.changed) return onSaved()
        viewModelScope.launch {
            // Checked again here, not only on opening: a sync from Home may have stopped on a conflict since.
            if (repository.needsChoice(kbName)) {
                _state.update {
                    it.copy(error = "Not saved: this knowledge base has a sync conflict to resolve first. Your text is still here.")
                }
                return@launch
            }
            val written = withContext(Dispatchers.IO) {
                runCatching { kb.write(file, FrontmatterParser.withUpdated(current.text, LocalDate.now())) }
            }
            written.fold(
                onSuccess = { onSaved() },
                onFailure = { error -> _state.update { it.copy(error = "Not saved: ${error.message}") } },
            )
        }
    }
}
