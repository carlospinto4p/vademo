package com.cpinto.vademo.data

import android.content.Context
import android.util.Log
import com.cpinto.vademo.git.Author
import com.cpinto.vademo.git.ConflictFile
import com.cpinto.vademo.git.GitSync
import com.cpinto.vademo.git.LocalState
import com.cpinto.vademo.git.SyncOutcome
import com.cpinto.vademo.kb.KnowledgeBase
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.NoteIndexCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        val current = settings.current()
        val git = gitSync(current)
        current.repoUrls.map { url ->
            async {
                val dir = dirFor(url)
                when (val local = git.inspect(dir)) {
                    LocalState.Missing -> KbInfo(dir.name, url, cloned = false, unsyncedEdits = 0, needsChoice = false)
                    is LocalState.Ready -> KbInfo(dir.name, url, cloned = true, local.changes.size, local.merging)
                    is LocalState.Broken -> KbInfo(
                        dir.name, url, cloned = false, unsyncedEdits = 0, needsChoice = false, problem = local.message,
                    )
                }
            }
        }.awaitAll()
    }

    fun knowledgeBase(name: String): KnowledgeBase = KnowledgeBase(File(root, name))

    // Logged so the cost of reading every note can be read on a phone:
    // adb logcat -s vademo
    private val indexes = NoteIndexCache { index, millis ->
        Log.i("vademo", "Read ${index.notes.size} notes of ${index.kbName} in $millis ms")
    }

    /** Every note of [name] with its links, read again only when a note file changed. */
    suspend fun index(name: String): NoteIndex = withContext(Dispatchers.IO) { indexes.of(knowledgeBase(name)) }

    /** The index of every downloaded knowledge base, in the order of the settings. */
    suspend fun indexes(): List<NoteIndex> = withContext(Dispatchers.IO) { downloaded().map(indexes::of) }

    /** Whether [name] stopped on a conflict, so editing waits for a choice. Cheaper than [list]. */
    suspend fun needsChoice(name: String): Boolean = withContext(Dispatchers.IO) {
        GitSync.isMerging(File(root, name))
    }

    /** The knowledge bases downloaded to this phone, in the order of the settings. */
    suspend fun downloaded(): List<KnowledgeBase> = withContext(Dispatchers.IO) {
        settings.current().repoUrls.map(::dirFor).filter { it.isDirectory }.map(::KnowledgeBase)
    }

    /**
     * Syncs every knowledge base at once. Two addresses that end in the same
     * folder are synced once, by the first, so no two syncs touch one clone.
     */
    suspend fun syncAll() = coroutineScope {
        val current = settings.current()
        current.repoUrls.distinctBy(::dirFor).map { url -> launch { sync(url, current) } }.joinAll()
    }

    suspend fun conflicts(name: String): Result<List<ConflictFile>> = withContext(Dispatchers.IO) {
        gitSync(settings.current()).conflicts(File(root, name))
    }

    suspend fun resolve(name: String, chosen: Map<String, String?>): SyncOutcome {
        val current = settings.current()
        val url = current.repoUrls.firstOrNull { dirFor(it).name == name }
            ?: return SyncOutcome.Failed("$name is no longer in Settings")
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

    /** `https://github.com/carlospinto4p/kb-distributed-systems.git` → `files/kb/kb-distributed-systems`. */
    private fun dirFor(url: String): File =
        File(root, url.trimEnd('/').substringAfterLast('/').removeSuffix(".git"))

    private fun gitSync(current: Settings) = GitSync(
        credentials = current.token.takeIf { it.isNotBlank() }?.let(::GitHubOnlyCredentials),
        author = Author(current.authorName, current.authorEmail),
    )
}
