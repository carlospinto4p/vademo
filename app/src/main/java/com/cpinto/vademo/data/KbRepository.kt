package com.cpinto.vademo.data

import android.content.Context
import com.cpinto.vademo.git.Author
import com.cpinto.vademo.git.ConflictFile
import com.cpinto.vademo.git.GitSync
import com.cpinto.vademo.git.LocalState
import com.cpinto.vademo.git.SyncOutcome
import com.cpinto.vademo.kb.KnowledgeBase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class KbInfo(
    val name: String,
    val remoteUrl: String,
    val cloned: Boolean,
    val unsyncedEdits: Int,
    val needsChoice: Boolean,
    /** Set when the local copy exists but git cannot read it. */
    val problem: String? = null,
)

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val outcome: SyncOutcome) : SyncState
}

/** The local clones of every configured knowledge base, and syncing them with GitHub. */
@Singleton
class KbRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsRepository,
) {
    private val root = File(context.filesDir, "kb")

    private val _syncStates = MutableStateFlow<Map<String, SyncState>>(emptyMap())
    val syncStates: StateFlow<Map<String, SyncState>> = _syncStates

    suspend fun list(): List<KbInfo> = withContext(Dispatchers.IO) {
        val git = gitSync(settings.current())
        settings.current().repoUrls.map { url ->
            val dir = dirFor(url)
            when (val local = git.inspect(dir)) {
                LocalState.Missing -> KbInfo(dir.name, url, cloned = false, unsyncedEdits = 0, needsChoice = false)
                is LocalState.Ready -> KbInfo(dir.name, url, cloned = true, local.changes.size, local.merging)
                is LocalState.Broken -> KbInfo(
                    dir.name, url, cloned = false, unsyncedEdits = 0, needsChoice = false, problem = local.message,
                )
            }
        }
    }

    fun knowledgeBase(name: String): KnowledgeBase = KnowledgeBase(File(root, name))

    suspend fun syncAll() {
        val current = settings.current()
        for (url in current.repoUrls) sync(url, current)
    }

    suspend fun conflicts(name: String): List<ConflictFile> = withContext(Dispatchers.IO) {
        gitSync(settings.current()).conflicts(File(root, name))
    }

    suspend fun resolve(name: String, chosen: Map<String, String?>): SyncOutcome {
        val current = settings.current()
        val url = current.repoUrls.first { dirFor(it).name == name }
        setState(name, SyncState.Running)
        val outcome = withContext(Dispatchers.IO) {
            gitSync(current).resolve(url, File(root, name), chosen)
        }
        setState(name, SyncState.Done(outcome))
        return outcome
    }

    private suspend fun sync(url: String, current: Settings) {
        val dir = dirFor(url)
        setState(dir.name, SyncState.Running)
        val outcome = withContext(Dispatchers.IO) { gitSync(current).sync(url, dir) }
        setState(dir.name, SyncState.Done(outcome))
    }

    private fun setState(name: String, state: SyncState) =
        _syncStates.update { it + (name to state) }

    /** `https://github.com/carlospinto4p/kb-business.git` → `files/kb/kb-business`. */
    private fun dirFor(url: String): File =
        File(root, url.trimEnd('/').substringAfterLast('/').removeSuffix(".git"))

    private fun gitSync(current: Settings) = GitSync(
        // The token goes in as the HTTPS password. Unverified, written from
        // recall: that GitHub accepts any non-empty user name ("oauth2") for
        // a personal access token. Check against github.com before relying
        // on it.
        credentials = current.token.takeIf { it.isNotBlank() }
            ?.let { UsernamePasswordCredentialsProvider("oauth2", it) },
        author = Author(current.authorName, current.authorEmail),
    )
}
