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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.data.Settings
import kotlinx.coroutines.flow.update

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
                label = { Text("GitHub access token") },
                isError = form.tokenUnreadable && form.token.isEmpty(),
                supportingText = {
                    Text(
                        if (form.tokenUnreadable && form.token.isEmpty()) {
                            "The saved token can no longer be read on this phone. Enter it again."
                        } else {
                            "Only for private repositories and for sending edits. Needs the repo scope " +
                                "(classic) or Contents: Read and write (fine-grained)."
                        },
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.authorName,
                onValueChange = { v -> viewModel.update { it.copy(authorName = v) } },
                label = { Text("Name on commits") },
                supportingText = { Text("Needed to send edits.") },
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
