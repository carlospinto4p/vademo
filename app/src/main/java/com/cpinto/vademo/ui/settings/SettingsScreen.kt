package com.cpinto.vademo.ui.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cpinto.vademo.data.GitHubRepo
import com.cpinto.vademo.data.GitHubRepoFinder
import com.cpinto.vademo.data.KbAddress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDone: () -> Unit,
    /** After Save has stored the settings; Back without saving is [onDone]. */
    onSaved: () -> Unit = onDone,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
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
                onValueChange = viewModel::updateToken,
                label = { Text("GitHub access token") },
                isError = form.tokenUnreadable && form.token.isEmpty(),
                supportingText = {
                    Text(
                        if (form.tokenUnreadable && form.token.isEmpty()) {
                            "The saved token can no longer be read on this phone. Enter it again."
                        } else {
                            "For private repositories, for sending edits and for listing your knowledge " +
                                "bases. Needs the repo scope (classic) or Contents: Read and write (fine-grained)."
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

            KnowledgeBaseList(form, onRemove = viewModel::remove)
            OutlinedTextField(
                value = form.newAddress,
                onValueChange = viewModel::updateNewAddress,
                label = { Text("Add by address") },
                placeholder = { Text("owner/kb-name") },
                isError = form.newAddressError != null,
                supportingText = {
                    Text(form.newAddressError ?: "owner/name on GitHub, or any HTTPS clone address.")
                },
                trailingIcon = {
                    IconButton(onClick = { viewModel.addTyped() }, enabled = form.newAddress.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = "Add knowledge base")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.addTyped() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            OnGitHub(form, onFind = viewModel::findOnGitHub, onAdd = viewModel::addFound)

            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = form.loaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

/** The knowledge bases that sync, one row each, with what is known of where each lives. */
@Composable
private fun KnowledgeBaseList(form: SettingsForm, onRemove: (String) -> Unit) {
    val found = (form.github as? GitHubList.Shown)?.repos.orEmpty().associateBy { KbAddress.of(it.cloneUrl).key }
    Column(Modifier.animateContentSize()) {
        Text("Knowledge bases", style = MaterialTheme.typography.titleMedium)
        if (form.repos.isEmpty()) {
            Text(
                "None yet. Add one from GitHub below, or by its address.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        form.repos.forEach { url ->
            val address = KbAddress.of(url)
            val repo = found[address.key]
            val (icon, where) = when {
                repo?.isPrivate == true -> Icons.Filled.Lock to "Private"
                repo != null -> Icons.Filled.Public to "Public"
                else -> Icons.Filled.Storage to null
            }
            ListItem(
                headlineContent = { Text(address.name) },
                supportingContent = {
                    Text(listOfNotNull(placeOf(address), where).joinToString(" · "))
                },
                leadingContent = { Icon(icon, contentDescription = where) },
                trailingContent = {
                    IconButton(onClick = { onRemove(url) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${address.name}")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        form.reposError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** `owner` on GitHub, `host/owner` anywhere else, the whole address when it cannot be read. */
private fun placeOf(address: KbAddress): String = when {
    address.host.isEmpty() -> address.url
    address.isGitHub -> address.owner
    else -> "${address.host}/${address.owner}"
}

/** The token's `kb-` repositories that are not in the list yet, each with a button to add it. */
@Composable
private fun OnGitHub(form: SettingsForm, onFind: () -> Unit, onAdd: (GitHubRepo) -> Unit) {
    Column(Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "On your GitHub",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (form.token.isNotBlank() && form.github != GitHubList.NotAsked) {
                IconButton(onClick = onFind, enabled = form.github != GitHubList.Loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Look again on GitHub")
                }
            }
        }
        val hint: @Composable (String) -> Unit = {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        val prefix = GitHubRepoFinder.PREFIX
        if (form.token.isBlank()) {
            hint("Enter a token above to list your $prefix repositories, public and private.")
        } else when (val github = form.github) {
            GitHubList.NotAsked -> OutlinedButton(onClick = onFind, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("List my $prefix repositories")
            }
            GitHubList.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            is GitHubList.Failed -> {
                Text(
                    github.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                TextButton(onClick = onFind) { Text("Try again") }
            }
            is GitHubList.Shown -> {
                val listed = form.repos.map { KbAddress.of(it).key }.toSet()
                val toAdd = github.repos.filter { KbAddress.of(it.cloneUrl).key !in listed }
                when {
                    github.repos.isEmpty() -> hint("The token sees no repository whose name starts with $prefix.")
                    toAdd.isEmpty() -> hint("All ${github.repos.size} of them are in the list.")
                }
                toAdd.forEach { repo -> FoundRow(repo, form.repos, onAdd) }
            }
        }
    }
}

@Composable
private fun FoundRow(repo: GitHubRepo, listed: List<String>, onAdd: (GitHubRepo) -> Unit) {
    val conflict = KbAddress.conflict(listed, KbAddress.of(repo.cloneUrl))
    val icon: ImageVector = if (repo.isPrivate) Icons.Filled.Lock else Icons.Filled.Public
    val details = listOfNotNull(
        if (repo.isPrivate) "Private" else "Public",
        "Archived".takeIf { repo.archived },
        repo.description?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(repo.fullName) },
        supportingContent = { Text(if (conflict != null) "Another in the list has the same name" else details) },
        leadingContent = { Icon(icon, contentDescription = if (repo.isPrivate) "Private" else "Public") },
        trailingContent = {
            TextButton(
                onClick = { onAdd(repo) },
                enabled = conflict == null,
                modifier = Modifier.semantics { contentDescription = "Add ${repo.fullName}" },
            ) { Text("Add") }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
