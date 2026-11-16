package com.cpinto.vademo.ui.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
            repository.conflicts(kbName).fold(
                onSuccess = { files ->
                    _state.value = ConflictState(files, files.associate { it.path to Choice(handText = it.mine.orEmpty()) })
                },
                onFailure = { error ->
                    _state.value = ConflictState(error = "Could not read the conflicting notes: ${error.message}")
                },
            )
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
