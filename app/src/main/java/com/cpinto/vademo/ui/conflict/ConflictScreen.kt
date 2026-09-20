package com.cpinto.vademo.ui.conflict

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.git.ConflictFile
import com.cpinto.vademo.git.SyncOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Keep { PHONE, GITHUB, BY_HAND }

data class Choice(val keep: Keep? = null, val handText: String = "")

data class ConflictState(
    val files: List<ConflictFile> = emptyList(),
    val choices: Map<String, Choice> = emptyMap(),
    val working: Boolean = false,
    val error: String? = null,
) {
    val complete: Boolean get() = files.isNotEmpty() && files.all { choices[it.path]?.keep != null }
}

@HiltViewModel
class ConflictViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])

    private val _state = MutableStateFlow(ConflictState())
    val state: StateFlow<ConflictState> = _state

    fun load() {
        viewModelScope.launch {
            val files = repository.conflicts(kbName)
            _state.value = ConflictState(files, files.associate { it.path to Choice(handText = it.mine.orEmpty()) })
        }
    }

    fun choose(path: String, keep: Keep) = _state.update {
        it.copy(choices = it.choices + (path to it.choices.getValue(path).copy(keep = keep)))
    }

    fun editByHand(path: String, text: String) = _state.update {
        it.copy(choices = it.choices + (path to Choice(Keep.BY_HAND, text)))
    }

    fun finish(onDone: () -> Unit) {
        val current = _state.value
        val chosen = current.files.associate { file ->
            val choice = current.choices.getValue(file.path)
            file.path to when (choice.keep) {
                Keep.PHONE -> file.mine
                Keep.GITHUB -> file.theirs
                Keep.BY_HAND, null -> choice.handText
            }
        }
        _state.update { it.copy(working = true, error = null) }
        viewModelScope.launch {
            when (val outcome = repository.resolve(kbName, chosen)) {
                is SyncOutcome.Synced -> onDone()
                is SyncOutcome.Conflict -> load()
                is SyncOutcome.Failed -> _state.update { it.copy(working = false, error = outcome.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictScreen(onDone: () -> Unit, viewModel: ConflictViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose what to keep") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("These notes were changed on this phone and on GitHub since the last sync.")
            state.files.forEach { file ->
                FileChoice(
                    file = file,
                    choice = state.choices[file.path] ?: Choice(),
                    onChoose = { viewModel.choose(file.path, it) },
                    onHandText = { viewModel.editByHand(file.path, it) },
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { viewModel.finish(onDone) },
                enabled = state.complete && !state.working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.working) "Syncing…" else "Keep these and sync") }
        }
    }
}

@Composable
private fun FileChoice(
    file: ConflictFile,
    choice: Choice,
    onChoose: (Keep) -> Unit,
    onHandText: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(file.path, style = MaterialTheme.typography.titleMedium)
            Version("On this phone", file.mine)
            Version("On GitHub", file.theirs)
            Option("Keep this phone's version", choice.keep == Keep.PHONE) { onChoose(Keep.PHONE) }
            Option("Keep GitHub's version", choice.keep == Keep.GITHUB) { onChoose(Keep.GITHUB) }
            Option("Combine them by hand", choice.keep == Keep.BY_HAND) { onChoose(Keep.BY_HAND) }
            if (choice.keep == Keep.BY_HAND) {
                OutlinedTextField(
                    value = choice.handText,
                    onValueChange = onHandText,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                )
            }
        }
    }
}

@Composable
private fun Version(label: String, text: String?) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Text(
        text ?: "(deleted)",
        style = TextStyle(fontFamily = FontFamily.Monospace),
        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
