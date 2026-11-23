package com.cpinto.vademo.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.data.KbInfo
import com.cpinto.vademo.data.SyncState
import com.cpinto.vademo.git.SyncOutcome
import com.cpinto.vademo.ui.kb.KbStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenKb: (KbStart) -> Unit,
    /** Settings were just saved, to be said once; then [onSettingsSavedShown]. */
    settingsSaved: Boolean = false,
    onSettingsSavedShown: () -> Unit = {},
    onResolve: (String) -> Unit,
    onSettings: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start(onOpenKb) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(settingsSaved) {
        if (settingsSaved) {
            snackbar.showSnackbar("Settings saved")
            onSettingsSavedShown()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("vademo") },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = viewModel::syncAll, enabled = state.canSync) {
                        // A ring while any knowledge base syncs, so a slow sync shows before the first row changes.
                        if (state.syncing) {
                            CircularProgressIndicator(
                                Modifier.size(24.dp).semantics { contentDescription = "Syncing" },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = "Sync")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (!state.canWrite) {
                // Keyed and animated like the rows, so it fades in and out and the list slides instead of jumping.
                item(key = SETUP_CARD) {
                    Card(Modifier.animateItem().fillMaxWidth().padding(16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (state.tokenUnreadable) {
                                    "The saved GitHub token can no longer be read on this phone. " +
                                        "Enter it again in Settings to sync private knowledge bases and send edits."
                                } else {
                                    "Public knowledge bases sync without setup. To sync a private one, " +
                                        "or to send your edits to GitHub, add a GitHub token, name and email."
                                },
                            )
                            Button(onClick = onSettings) { Text("Open settings") }
                        }
                    }
                }
            }
            items(state.kbs, key = { it.name }) { kb ->
                Column(Modifier.animateItem()) {
                    KbRow(
                        kb = kb,
                        sync = state.syncStates[kb.name] ?: SyncState.Idle,
                        onClick = {
                            when {
                                kb.needsChoice -> onResolve(kb.name)
                                kb.cloned -> viewModel.open(kb.name, onOpenKb)
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The setup card's key in the list; GitHub allows no `#` in a repository's name, so no row's key clashes with it. */
private const val SETUP_CARD = "#setup"

@Composable
private fun KbRow(kb: KbInfo, sync: SyncState, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = kb.cloned, onClick = onClick),
        headlineContent = { Text(kb.name) },
        supportingContent = {
            // Grows and shrinks as the sync line comes and goes, instead of jumping.
            Column(Modifier.animateContentSize()) {
                Text(statusLine(kb))
                syncLine(sync)?.let { (text, isProblem) ->
                    Text(
                        text,
                        color = if (isProblem) MaterialTheme.colorScheme.error else Color.Unspecified,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

private fun statusLine(kb: KbInfo): String = when {
    kb.problem != null -> "The copy on this phone is damaged: ${kb.problem}"
    !kb.cloned -> "Not downloaded yet: tap Sync"
    kb.needsChoice -> "Changed here and on GitHub: tap to choose"
    kb.unsyncedEdits == 1 -> "1 edit not synced"
    kb.unsyncedEdits > 1 -> "${kb.unsyncedEdits} edits not synced"
    else -> "Synced"
}

/** The result of the last sync, and whether it needs attention. */
private fun syncLine(state: SyncState): Pair<String, Boolean>? = when (state) {
    SyncState.Idle -> null
    SyncState.Running -> "Syncing…" to false
    is SyncState.Done -> when (val outcome = state.outcome) {
        is SyncOutcome.Synced -> describe(outcome) to false
        is SyncOutcome.Conflict -> "Stopped: ${outcome.paths.size} file(s) changed on both sides" to true
        is SyncOutcome.Failed -> outcome.message to true
    }
}

private fun describe(outcome: SyncOutcome.Synced): String {
    if (outcome.cloned) return "Downloaded"
    val parts = buildList {
        if (outcome.committed.isNotEmpty()) add("sent ${outcome.committed.size} edited file(s)")
        if (outcome.pulled > 0) add("got ${outcome.pulled} change(s)")
    }
    return if (parts.isEmpty()) "Up to date" else parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}
