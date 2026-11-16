package com.cpinto.vademo.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(FrontmatterParser.withUpdated(current.text, LocalDate.now()))
                }
            }
            written.fold(
                onSuccess = { onSaved() },
                onFailure = { error -> _state.update { it.copy(error = "Not saved: ${error.message}") } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(onDone: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.load() }

    val leave = { if (state.changed) confirmDiscard = true else onDone() }
    BackHandler(onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isNew) "New note" else "Edit") },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.blocked) {
                        IconButton(onClick = { viewModel.save(onDone) }, enabled = state.loaded) {
                            Icon(Icons.Filled.Check, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.blocked) {
            Text(
                "This knowledge base has a note changed both here and on GitHub. " +
                    "Choose which version to keep from the home screen before editing.",
                Modifier.padding(padding).padding(16.dp),
            )
        } else {
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                if (state.loaded) {
                    OutlinedTextField(
                        value = state.text,
                        onValueChange = viewModel::onTextChange,
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard your changes?") },
            confirmButton = { TextButton(onClick = onDone) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}
