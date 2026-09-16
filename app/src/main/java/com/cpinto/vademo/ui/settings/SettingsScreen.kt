package com.cpinto.vademo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.Settings
import com.cpinto.vademo.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsForm(
    val loaded: Boolean = false,
    val token: String = "",
    val authorName: String = "",
    val authorEmail: String = "",
    val repos: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    fun load() {
        if (_form.value.loaded) return
        viewModelScope.launch {
            val current = repository.current()
            _form.value = SettingsForm(
                loaded = true,
                token = current.token,
                authorName = current.authorName,
                authorEmail = current.authorEmail,
                repos = current.repoUrls.joinToString("\n"),
            )
        }
    }

    fun update(change: (SettingsForm) -> SettingsForm) = _form.update(change)

    fun save(onSaved: () -> Unit) {
        val form = _form.value
        viewModelScope.launch {
            repository.save(
                Settings(
                    token = form.token,
                    authorName = form.authorName,
                    authorEmail = form.authorEmail,
                    repoUrls = form.repos.lines().map { it.trim() }.filter { it.isNotEmpty() },
                )
            )
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onDone: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.token,
                onValueChange = { v -> viewModel.update { it.copy(token = v) } },
                label = { Text("GitLab access token") },
                supportingText = { Text("Needs the write_repository scope.") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.authorName,
                onValueChange = { v -> viewModel.update { it.copy(authorName = v) } },
                label = { Text("Name on commits") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.authorEmail,
                onValueChange = { v -> viewModel.update { it.copy(authorEmail = v) } },
                label = { Text("Email on commits") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.repos,
                onValueChange = { v -> viewModel.update { it.copy(repos = v) } },
                label = { Text("Knowledge bases") },
                supportingText = { Text("One HTTPS clone address per line.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = form.loaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}
