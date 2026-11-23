package com.cpinto.vademo.ui.conflict

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.git.ConflictFile

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
    val scroll = rememberScrollState()
    val card = CardDefaults.cardColors().containerColor
    Box(Modifier.fillMaxWidth()) {
        Text(
            text ?: "(deleted)",
            style = TextStyle(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(scroll),
        )
        // A long version fades out at the bottom while there is more of it below.
        if (scroll.canScrollForward) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, card))),
            )
        }
    }
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
