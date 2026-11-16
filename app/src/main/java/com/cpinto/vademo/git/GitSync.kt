package com.cpinto.vademo.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.dircache.DirCacheEntry
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.FileMode
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryState
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

sealed interface SyncOutcome {
    data class Synced(
        val cloned: Boolean = false,
        val committed: List<String> = emptyList(),
        val pulled: Int = 0,
        val pushed: Int = 0,
    ) : SyncOutcome

    /** Both sides changed the same lines. Nothing was overwritten; the user must choose. */
    data class Conflict(val paths: List<String>) : SyncOutcome

    data class Failed(val message: String) : SyncOutcome
}

/** What is on disk for one knowledge base, read without ever throwing. */
sealed interface LocalState {
    data object Missing : LocalState
    data class Ready(val changes: List<String>, val merging: Boolean) : LocalState
    /** A `.git` folder git cannot open, e.g. left by a copy that did not finish. */
    data class Broken(val message: String) : LocalState
}

/** One conflicting file: its content on this device and on GitHub. `null` means deleted on that side. */
data class ConflictFile(val path: String, val mine: String?, val theirs: String?)

data class Author(val name: String, val email: String)

/**
 * Keeps one local clone in step with its remote: commit what was edited here,
 * merge what changed there, push the result.
 *
 * A conflicting merge is left in git's own merging state rather than aborted,
 * so it survives the app being closed and nothing on either side is lost.
 * [conflicts] and [resolve] finish it.
 */
class GitSync(
    private val credentials: CredentialsProvider?,
    private val author: Author,
) {

    fun sync(remoteUrl: String, dir: File): SyncOutcome = runCatching {
        if (!File(dir, ".git").isDirectory) return clone(remoteUrl, dir)
        Git.open(dir).use { git ->
            turnOffSymlinks(git)
            if (git.repository.repositoryState == RepositoryState.MERGING) {
                return SyncOutcome.Conflict(conflictingPaths(git))
            }
            val committed = commitLocalChanges(git)
            pullAndPush(git, committed)
        }
    }.getOrElse { SyncOutcome.Failed(describe(it)) }

    fun inspect(dir: File): LocalState {
        if (!File(dir, ".git").exists()) return LocalState.Missing
        return runCatching {
            Git.open(dir).use { git ->
                val status = git.status().call()
                LocalState.Ready(
                    changes = (status.modified + status.untracked + status.missing + status.added +
                        status.changed + status.removed).sorted(),
                    merging = git.repository.repositoryState == RepositoryState.MERGING,
                )
            }
        }.getOrElse { LocalState.Broken(describe(it)) }
    }

    /** Files edited on this device and not yet synced. */
    fun localChanges(dir: File): List<String> = (inspect(dir) as? LocalState.Ready)?.changes.orEmpty()

    /** The conflicting files, or a failure if the clone cannot be read. */
    fun conflicts(dir: File): Result<List<ConflictFile>> = runCatching {
        Git.open(dir).use { git ->
            val repo = git.repository
            val mergeHead = repo.readMergeHeads()?.firstOrNull() ?: return@runCatching emptyList()
            val head = repo.resolve(Constants.HEAD)
            conflictingPaths(git).map { path ->
                ConflictFile(path, readBlob(repo, head, path), readBlob(repo, mergeHead, path))
            }
        }
    }

    /**
     * Finishes a conflicting merge with the content chosen for each file
     * (`null` deletes it), then syncs so the merge reaches the remote.
     */
    fun resolve(remoteUrl: String, dir: File, chosen: Map<String, String?>): SyncOutcome =
        runCatching {
            Git.open(dir).use { git ->
                val remaining = conflictingPaths(git) - chosen.keys
                require(remaining.isEmpty()) { "No choice made for ${remaining.joinToString()}" }
                for ((path, content) in chosen) {
                    val file = File(dir, path)
                    if (content == null) {
                        file.delete()
                        git.rm().addFilepattern(path).call()
                    } else {
                        file.parentFile?.mkdirs()
                        // Replaces a symlink left by an older clone instead of writing through it.
                        file.delete()
                        file.writeText(content)
                        git.add().addFilepattern(path).call()
                    }
                }
                val ident = ident()
                git.commit().setAuthor(ident).setCommitter(ident)
                    .setMessage("note: merge phone and GitHub edits to ${names(chosen.keys)}")
                    .call()
            }
            sync(remoteUrl, dir)
        }.getOrElse { SyncOutcome.Failed(describe(it)) }

    /**
     * Clones into a sibling folder and renames it into place only when
     * complete, so an interrupted download never looks like a finished one.
     */
    private fun clone(remoteUrl: String, dir: File): SyncOutcome {
        val partial = File(dir.parentFile, "${dir.name}.downloading")
        partial.deleteRecursively()
        partial.parentFile?.mkdirs()
        try {
            Git.cloneRepository()
                .setURI(remoteUrl)
                .setDirectory(partial)
                .setCredentialsProvider(credentials)
                .setTimeout(TIMEOUT_SECONDS)
                .call()
                .use(::turnOffSymlinks)
            check(partial.renameTo(dir)) { "Could not move the download into place" }
        } finally {
            partial.deleteRecursively()
        }
        return SyncOutcome.Synced(cloned = true)
    }

    private fun commitLocalChanges(git: Git): List<String> {
        val status = git.status().call()
        val written = status.modified + status.untracked
        // Already staged, e.g. by a sync killed between staging and committing.
        val staged = status.added + status.changed + status.removed
        val changed = (written + status.missing + staged).distinct().sorted()
        if (changed.isEmpty()) return emptyList()
        // Never commit under a made-up identity: stop before anything is staged.
        check(author.name.isNotBlank() && author.email.isNotBlank()) {
            "Add your name and email in Settings to send your edits"
        }
        // Staging the paths the status found, rather than ".", spares two
        // more walks of the whole working tree.
        if (written.isNotEmpty()) {
            git.add().apply { written.forEach(::addFilepattern) }.call()
        }
        if (status.missing.isNotEmpty()) {
            git.rm().setCached(true).apply { status.missing.forEach(::addFilepattern) }.call()
        }
        val ident = ident()
        git.commit().setAuthor(ident).setCommitter(ident)
            .setMessage("note: edit ${names(changed)} from phone")
            .call()
        return changed
    }

    private fun pullAndPush(git: Git, committed: List<String>): SyncOutcome {
        var pulled = 0
        repeat(PUSH_ATTEMPTS) {
            git.fetch().setCredentialsProvider(credentials).setTimeout(TIMEOUT_SECONDS).call()
            val repo = git.repository
            val branch = repo.branch
            val remote = repo.resolve("refs/remotes/origin/$branch")
                ?: return SyncOutcome.Failed("GitHub has no branch named $branch")

            pulled += countCommits(repo, include = remote, exclude = repo.resolve(Constants.HEAD))
            val merge = git.merge()
                .include(remote)
                .setFastForward(MergeCommand.FastForwardMode.FF)
                .setMessage("note: merge GitHub changes into phone edits")
                .call()
            when (merge.mergeStatus) {
                MergeResult.MergeStatus.CONFLICTING ->
                    return SyncOutcome.Conflict(conflictingPaths(git))
                MergeResult.MergeStatus.FAILED, MergeResult.MergeStatus.ABORTED,
                MergeResult.MergeStatus.CHECKOUT_CONFLICT, MergeResult.MergeStatus.NOT_SUPPORTED ->
                    return SyncOutcome.Failed("Could not merge GitHub changes: ${merge.mergeStatus}")
                else -> Unit
            }

            val ahead = countCommits(repo, include = repo.resolve(Constants.HEAD), exclude = remote)
            if (ahead == 0) return SyncOutcome.Synced(committed = committed, pulled = pulled)

            val updates = git.push()
                .setCredentialsProvider(credentials)
                .setTimeout(TIMEOUT_SECONDS)
                .call()
                .flatMap { it.remoteUpdates }
            val rejected = updates.filter { it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE }
            if (rejected.isEmpty()) {
                return SyncOutcome.Synced(committed = committed, pulled = pulled, pushed = ahead)
            }
            // Someone pushed between our fetch and our push: fetch and merge again.
            if (rejected.any { it.status != RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD &&
                    it.status != RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED }) {
                return SyncOutcome.Failed("GitHub refused the push: ${rejected.joinToString { "${it.status} ${it.message.orEmpty()}" }}")
            }
        }
        return SyncOutcome.Failed("GitHub kept changing during sync; try again")
    }

    /**
     * Makes git write a symlink from GitHub as a plain file holding its
     * target, and rewrites the ones already on disk that way. A followed
     * symlink would let a note read or overwrite the app's private files.
     * Git keeps recording them as symlinks, so syncing sends nothing back.
     */
    private fun turnOffSymlinks(git: Git) {
        val repo = git.repository
        val config = repo.config
        if (!config.getBoolean(ConfigConstants.CONFIG_CORE_SECTION, ConfigConstants.CONFIG_KEY_SYMLINKS, true)) return
        config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_SYMLINKS, false)
        config.save()
        val cache = repo.readDirCache()
        val links = (0 until cache.entryCount).map(cache::getEntry)
            .filter { it.fileMode == FileMode.SYMLINK && it.stage == DirCacheEntry.STAGE_0 }
            .map { it.pathString }
        if (links.isEmpty()) return
        links.forEach { File(repo.workTree, it).delete() }
        git.checkout().addPaths(links).call()
    }

    private fun conflictingPaths(git: Git): List<String> = git.status().call().conflicting.sorted()

    private fun countCommits(repo: Repository, include: ObjectId?, exclude: ObjectId?): Int {
        if (include == null) return 0
        RevWalk(repo).use { walk ->
            walk.markStart(walk.parseCommit(include))
            if (exclude != null) walk.markUninteresting(walk.parseCommit(exclude))
            return walk.count()
        }
    }

    private fun readBlob(repo: Repository, commitId: ObjectId?, path: String): String? {
        if (commitId == null) return null
        RevWalk(repo).use { walk ->
            val tree = walk.parseCommit(commitId).tree
            TreeWalk.forPath(repo, path, tree)?.use { treeWalk ->
                return String(repo.open(treeWalk.getObjectId(0)).bytes, Charsets.UTF_8)
            }
        }
        return null
    }

    private fun ident() = PersonIdent(author.name, author.email)

    private fun names(paths: Collection<String>): String {
        val stems = paths.map { it.substringAfterLast('/').removeSuffix(".md") }
        return if (stems.size <= 3) stems.joinToString(", ") else "${stems.size} notes"
    }

    private fun describe(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.mapNotNull { it.message }.distinct()
        return causes.joinToString(": ").ifBlank { error::class.java.simpleName }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 60
        private const val PUSH_ATTEMPTS = 3

        /**
         * Whether the clone in [dir] stopped on a conflict that waits for a
         * choice. Reads git's merge state only, without the status scan of
         * [inspect], so it is cheap enough to ask before every save.
         */
        fun isMerging(dir: File): Boolean = runCatching {
            Git.open(dir).use { it.repository.repositoryState == RepositoryState.MERGING }
        }.getOrDefault(false)
    }
}
