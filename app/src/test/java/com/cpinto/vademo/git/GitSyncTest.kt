package com.cpinto.vademo.git

import org.eclipse.jgit.api.Git
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files

/**
 * Runs [GitSync] against real repositories: a bare repository stands in for
 * GitHub, a second clone for the computer, and [phone] for the app's copy.
 */
class GitSyncTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var remoteUrl: String
    private lateinit var computer: File
    private lateinit var phone: File
    private val sync = GitSync(credentials = null, author = Author("Phone", "phone@example.invalid"))

    @BeforeEach
    fun setUp() {
        val bare = File(tmp, "github.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close()
        remoteUrl = bare.toURI().toString()

        computer = File(tmp, "computer")
        // Cloning an empty repository would name the branch master; the bare
        // repository's HEAD points at main, as GitHub's does.
        Git.init().setInitialBranch("main").setDirectory(computer).call().use { git ->
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(remoteUrl)).call()
            File(computer, "ideas").mkdirs()
            File(computer, "ideas/tyfold.md").writeText("line one\nline two\nline three\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("note: first").setAuthor("Computer", "c@example.invalid").call()
            git.push().setRemote("origin").add("main").call()
            val config = git.repository.config
            config.setString("branch", "main", "remote", "origin")
            config.setString("branch", "main", "merge", "refs/heads/main")
            config.save()
        }
        phone = File(tmp, "phone/kb-distributed-systems")
    }

    private fun computerEdits(path: String, content: String) {
        Git.open(computer).use { git ->
            git.pull().call()
            File(computer, path).writeText(content)
            git.add().addFilepattern(path).call()
            git.commit().setMessage("note: computer edit").setAuthor("Computer", "c@example.invalid").call()
            git.push().call()
        }
    }

    private fun computerPulls(path: String): String = Git.open(computer).use { git ->
        git.pull().call()
        File(computer, path).readText()
    }

    @Test
    fun `first sync clones the repository`() {
        val outcome = sync.sync(remoteUrl, phone)
        assertEquals(SyncOutcome.Synced(cloned = true), outcome)
        assertEquals("line one\nline two\nline three\n", File(phone, "ideas/tyfold.md").readText())
    }

    @Test
    fun `sync pulls changes made on the computer`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "changed on computer\n")

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced
        assertEquals(1, outcome.pulled)
        assertEquals(0, outcome.pushed)
        assertEquals("changed on computer\n", File(phone, "ideas/tyfold.md").readText())
    }

    @Test
    fun `a reader with no name, email or token still downloads and pulls`() {
        val reader = GitSync(credentials = null, author = Author("", ""))
        assertEquals(SyncOutcome.Synced(cloned = true), reader.sync(remoteUrl, phone))

        computerEdits("ideas/tyfold.md", "changed on computer\n")
        val outcome = reader.sync(remoteUrl, phone) as SyncOutcome.Synced
        assertEquals(1, outcome.pulled)
        assertEquals("changed on computer\n", File(phone, "ideas/tyfold.md").readText())
    }

    @Test
    fun `edits already staged when the app was killed before committing are still pushed`() {
        sync.sync(remoteUrl, phone)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")
        File(phone, "ideas/new.md").writeText("new on phone\n")
        Git.open(phone).use { git ->
            git.add().addFilepattern("ideas/tyfold.md").addFilepattern("ideas/new.md").call()
        }

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced

        assertEquals(listOf("ideas/new.md", "ideas/tyfold.md"), outcome.committed)
        assertEquals(1, outcome.pushed)
        assertEquals(emptyList<String>(), sync.localChanges(phone))
        assertEquals("edited on phone\n", computerPulls("ideas/tyfold.md"))
        assertEquals("new on phone\n", File(computer, "ideas/new.md").readText())
    }

    @Test
    fun `a deletion already staged is still pushed`() {
        sync.sync(remoteUrl, phone)
        Git.open(phone).use { git -> git.rm().addFilepattern("ideas/tyfold.md").call() }

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced

        assertEquals(listOf("ideas/tyfold.md"), outcome.committed)
        assertEquals(emptyList<String>(), sync.localChanges(phone))
        Git.open(computer).use { it.pull().call() }
        assertFalse(File(computer, "ideas/tyfold.md").exists())
    }

    @Test
    fun `an edit without a name and email is refused, not committed under a made-up identity`() {
        val reader = GitSync(credentials = null, author = Author("", ""))
        reader.sync(remoteUrl, phone)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")

        val outcome = reader.sync(remoteUrl, phone)

        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
        assertTrue((outcome as SyncOutcome.Failed).message.contains("name and email"), outcome.message)
        assertEquals(listOf("ideas/tyfold.md"), reader.localChanges(phone))
        assertEquals("line one\nline two\nline three\n", computerPulls("ideas/tyfold.md"))
    }

    @Test
    fun `sync commits a phone edit and pushes it`() {
        sync.sync(remoteUrl, phone)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")
        assertEquals(listOf("ideas/tyfold.md"), sync.localChanges(phone))

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced
        assertEquals(listOf("ideas/tyfold.md"), outcome.committed)
        assertEquals(1, outcome.pushed)
        assertEquals("edited on phone\n", computerPulls("ideas/tyfold.md"))
        assertTrue(sync.localChanges(phone).isEmpty())

        Git.open(computer).use { git ->
            val last = git.log().setMaxCount(1).call().single()
            assertEquals("note: edit tyfold from phone", last.fullMessage)
            assertEquals("Phone", last.authorIdent.name)
        }
    }

    @Test
    fun `sync pushes new and deleted notes`() {
        sync.sync(remoteUrl, phone)
        File(phone, "ideas/new-idea.md").writeText("new\n")
        File(phone, "topics/consensus").mkdirs()
        File(phone, "topics/consensus/raft.md").writeText("in a new folder\n")
        File(phone, "ideas/tyfold.md").delete()

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced

        assertEquals(listOf("ideas/new-idea.md", "ideas/tyfold.md", "topics/consensus/raft.md"), outcome.committed)
        assertTrue(sync.localChanges(phone).isEmpty())
        Git.open(computer).use { it.pull().call() }
        assertTrue(File(computer, "ideas/new-idea.md").isFile)
        assertTrue(File(computer, "topics/consensus/raft.md").isFile)
        assertFalse(File(computer, "ideas/tyfold.md").exists())
    }

    @Test
    fun `edits to different lines on both sides merge without asking`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "line one CHANGED\nline two\nline three\n")
        File(phone, "ideas/tyfold.md").writeText("line one\nline two\nline three CHANGED\n")

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced
        assertEquals(1, outcome.pulled)
        assertEquals("line one CHANGED\nline two\nline three CHANGED\n", computerPulls("ideas/tyfold.md"))
    }

    @Test
    fun `edits to the same line stop with a conflict and keep both versions`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "line one\nCOMPUTER\nline three\n")
        File(phone, "ideas/tyfold.md").writeText("line one\nPHONE\nline three\n")

        val outcome = sync.sync(remoteUrl, phone)
        assertEquals(SyncOutcome.Conflict(listOf("ideas/tyfold.md")), outcome)
        assertTrue(GitSync.isMerging(phone))

        val conflict = sync.conflicts(phone).getOrThrow().single()
        assertEquals("line one\nPHONE\nline three\n", conflict.mine)
        assertEquals("line one\nCOMPUTER\nline three\n", conflict.theirs)

        // GitHub still has the computer's version: nothing was pushed or overwritten.
        assertEquals("line one\nCOMPUTER\nline three\n", computerPulls("ideas/tyfold.md"))
    }

    @Test
    fun `a conflict is still reported on the next sync`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").writeText("PHONE\n")
        sync.sync(remoteUrl, phone)

        assertEquals(SyncOutcome.Conflict(listOf("ideas/tyfold.md")), sync.sync(remoteUrl, phone))
    }

    @Test
    fun `resolving a conflict pushes the chosen content`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").writeText("PHONE\n")
        sync.sync(remoteUrl, phone)

        val outcome = sync.resolve(remoteUrl, phone, mapOf("ideas/tyfold.md" to "PHONE and COMPUTER\n"))

        assertInstanceOf(SyncOutcome.Synced::class.java, outcome)
        assertFalse(GitSync.isMerging(phone))
        assertEquals("PHONE and COMPUTER\n", computerPulls("ideas/tyfold.md"))
    }

    @Test
    fun `resolving requires a choice for every conflicting file`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").writeText("PHONE\n")
        sync.sync(remoteUrl, phone)

        val outcome = sync.resolve(remoteUrl, phone, emptyMap())

        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
        assertTrue(GitSync.isMerging(phone))
    }

    @Test
    fun `isMerging is false for a clean clone, a missing one and a damaged one`() {
        sync.sync(remoteUrl, phone)
        assertFalse(GitSync.isMerging(phone))
        assertFalse(GitSync.isMerging(File(tmp, "nowhere")))
        File(tmp, "broken/.git").mkdirs()
        assertFalse(GitSync.isMerging(File(tmp, "broken")))
    }

    @Test
    fun `an unreachable remote is reported as a failure`() {
        val outcome = sync.sync(File(tmp, "missing.git").toURI().toString(), File(tmp, "other"))
        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
    }

    @Test
    fun `a failed first download leaves nothing that looks downloaded`() {
        val target = File(tmp, "phone/kb-missing")
        sync.sync(File(tmp, "missing.git").toURI().toString(), target)

        assertEquals(LocalState.Missing, sync.inspect(target))
        assertFalse(target.exists())
    }

    @Test
    fun `a download left half done by a killed app is replaced`() {
        val partial = File(tmp, "phone/kb-distributed-systems.downloading")
        File(partial, ".git").mkdirs()
        File(partial, "ideas.md").writeText("half written")

        assertEquals(SyncOutcome.Synced(cloned = true), sync.sync(remoteUrl, phone))
        assertEquals("line one\nline two\nline three\n", File(phone, "ideas/tyfold.md").readText())
        assertFalse(partial.exists())
    }

    @Test
    fun `an empty folder in the way is downloaded into`() {
        phone.mkdirs()
        assertEquals(SyncOutcome.Synced(cloned = true), sync.sync(remoteUrl, phone))
        assertInstanceOf(LocalState.Ready::class.java, sync.inspect(phone))
    }

    @Test
    fun `a folder with files but no git is left alone and reported before downloading`() {
        File(phone, "notes").mkdirs()
        File(phone, "notes/kept.md").writeText("not from git\n")

        val outcome = sync.sync(remoteUrl, phone)

        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
        assertTrue((outcome as SyncOutcome.Failed).message.contains("not a downloaded copy"), outcome.message)
        assertEquals("not from git\n", File(phone, "notes/kept.md").readText())
        assertEquals(listOf("notes"), phone.list()!!.toList())
        assertFalse(File(tmp, "phone/kb-distributed-systems.downloading").exists())
    }

    @Test
    fun `an address of another repository with the same name is refused, naming the one downloaded`() {
        sync.sync(remoteUrl, phone)
        val other = File(tmp, "someone-else/kb-distributed-systems.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(other).call().close()
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")

        val outcome = sync.sync(other.toURI().toString(), phone)

        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
        assertTrue((outcome as SyncOutcome.Failed).message.contains("/github.git"), outcome.message)
        // Nothing was committed or sent anywhere.
        assertEquals(listOf("ideas/tyfold.md"), sync.localChanges(phone))
        assertEquals("line one\nline two\nline three\n", computerPulls("ideas/tyfold.md"))
    }

    @Test
    fun `the same address written with or without slash and dot git still syncs`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "changed on computer\n")

        val outcome = sync.sync(remoteUrl.trimEnd('/').removeSuffix(".git") + "/", phone)

        assertEquals(SyncOutcome.Synced(pulled = 1), outcome)
    }

    @Test
    fun `addresses of one GitHub repository match whatever form they take`() {
        val canonical = "https://github.com/owner/kb-notes.git"
        for (same in listOf(
            "https://github.com/owner/kb-notes",
            "https://github.com/owner/kb-notes/",
            "https://GitHub.com/Owner/KB-Notes.git",
            "git@github.com:owner/kb-notes.git",
            " https://github.com/owner/kb-notes.git ",
        )) {
            assertTrue(GitSync.sameRepository(canonical, same), same)
        }
        for (other in listOf(
            "https://github.com/someone-else/kb-notes.git",
            "https://gitlab.com/owner/kb-notes.git",
            "https://github.com/owner/kb-notes-2.git",
        )) {
            assertFalse(GitSync.sameRepository(canonical, other), other)
        }
    }

    @Test
    fun `inspect reports a ready clone with its edits`() {
        sync.sync(remoteUrl, phone)
        File(phone, "ideas/tyfold.md").writeText("edited\n")

        assertEquals(LocalState.Ready(listOf("ideas/tyfold.md"), merging = false), sync.inspect(phone))
    }

    @Test
    fun `inspect reports a damaged repository instead of throwing`() {
        // A .git folder without its objects and refs, as an interrupted copy leaves.
        File(tmp, "broken/.git").mkdirs()
        File(tmp, "broken/.git/HEAD").writeText("ref: refs/heads/main\n")

        assertInstanceOf(LocalState.Broken::class.java, sync.inspect(File(tmp, "broken")))
    }

    @Test
    fun `sync on a damaged repository fails without throwing`() {
        File(tmp, "broken/.git").mkdirs()
        assertInstanceOf(SyncOutcome.Failed::class.java, sync.sync(remoteUrl, File(tmp, "broken")))
    }

    @Test
    fun `reading conflicts of a damaged or missing repository fails without throwing`() {
        File(tmp, "broken/.git").mkdirs()
        assertTrue(sync.conflicts(File(tmp, "broken")).isFailure)
        assertTrue(sync.conflicts(File(tmp, "nowhere")).isFailure)
    }

    /**
     * Makes GitHub move on between the phone's fetch and its push, [times]
     * times in a row: commits made on the computer wait on a side branch,
     * and a `pre-push` hook in the phone's clone moves `main` to the next one.
     */
    private fun computerPushesDuringEachSync(times: Int) {
        val queue = File(tmp, "queue").apply { mkdirs() }
        Git.open(computer).use { git ->
            git.pull().call()
            repeat(times) { i ->
                File(computer, "ideas/race-$i.md").writeText("race $i\n")
                git.add().addFilepattern("ideas/race-$i.md").call()
                val commit = git.commit().setMessage("note: race $i").setAuthor("Computer", "c@example.invalid").call()
                File(queue, "$i").writeText(commit.name + "\n")
            }
            git.push().setRemote("origin").add("main:refs/heads/waiting").call()
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).setRef("origin/main").call()
        }
        val mainRef = File(tmp, "github.git/refs/heads/main")
        File(phone, ".git/hooks").mkdirs()
        File(phone, ".git/hooks/pre-push").apply {
            writeText(
                """
                #!/bin/sh
                cat > /dev/null
                next=${'$'}(ls "${queue.path}" | sort | head -n 1)
                [ -n "${'$'}next" ] || exit 0
                cat "${queue.path}/${'$'}next" > "${mainRef.path}"
                rm "${queue.path}/${'$'}next"
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }
    }

    @Test
    fun `a push rejected because GitHub moved on is merged and pushed again`() {
        sync.sync(remoteUrl, phone)
        computerPushesDuringEachSync(times = 1)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")

        val outcome = sync.sync(remoteUrl, phone) as SyncOutcome.Synced

        assertEquals(listOf("ideas/tyfold.md"), outcome.committed)
        assertEquals(1, outcome.pulled)
        assertEquals(2, outcome.pushed)
        assertEquals("edited on phone\n", computerPulls("ideas/tyfold.md"))
        assertEquals("race 0\n", File(computer, "ideas/race-0.md").readText())
    }

    @Test
    fun `a push rejected on every attempt gives up and keeps the edit`() {
        sync.sync(remoteUrl, phone)
        computerPushesDuringEachSync(times = 3)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")

        val outcome = sync.sync(remoteUrl, phone)

        assertEquals(SyncOutcome.Failed("GitHub kept changing during sync; try again"), outcome)
        assertEquals("line one\nline two\nline three\n", computerPulls("ideas/tyfold.md"))
        // The edit is committed on the phone, so the next sync sends it.
        assertInstanceOf(SyncOutcome.Synced::class.java, sync.sync(remoteUrl, phone))
        assertEquals("edited on phone\n", computerPulls("ideas/tyfold.md"))
        assertEquals("race 2\n", File(computer, "ideas/race-2.md").readText())
    }

    @Test
    fun `a push refused for another reason is reported, not retried`() {
        sync.sync(remoteUrl, phone)
        File(phone, "ideas/tyfold.md").writeText("edited on phone\n")
        // A lock git left behind on GitHub's side stops the update of main.
        File(tmp, "github.git/refs/heads/main.lock").writeText("")

        val outcome = sync.sync(remoteUrl, phone)

        assertInstanceOf(SyncOutcome.Failed::class.java, outcome)
        assertTrue((outcome as SyncOutcome.Failed).message.startsWith("GitHub refused the push"), outcome.message)
    }

    @Test
    fun `a branch GitHub does not have is reported`() {
        sync.sync(remoteUrl, phone)
        Git.open(phone).use { it.checkout().setCreateBranch(true).setName("draft").call() }

        assertEquals(SyncOutcome.Failed("GitHub has no branch named draft"), sync.sync(remoteUrl, phone))
    }

    @Test
    fun `a note deleted on the phone and edited on GitHub stops with a conflict`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").delete()

        assertEquals(SyncOutcome.Conflict(listOf("ideas/tyfold.md")), sync.sync(remoteUrl, phone))
        val conflict = sync.conflicts(phone).getOrThrow().single()
        assertEquals(null, conflict.mine)
        assertEquals("COMPUTER\n", conflict.theirs)
    }

    @Test
    fun `a note edited on the phone and deleted on GitHub stops with a conflict`() {
        sync.sync(remoteUrl, phone)
        Git.open(computer).use { git ->
            git.pull().call()
            git.rm().addFilepattern("ideas/tyfold.md").call()
            git.commit().setMessage("note: delete").setAuthor("Computer", "c@example.invalid").call()
            git.push().call()
        }
        File(phone, "ideas/tyfold.md").writeText("PHONE\n")

        assertEquals(SyncOutcome.Conflict(listOf("ideas/tyfold.md")), sync.sync(remoteUrl, phone))
        val conflict = sync.conflicts(phone).getOrThrow().single()
        assertEquals("PHONE\n", conflict.mine)
        assertEquals(null, conflict.theirs)
    }

    @Test
    fun `choosing the deletion in a conflict deletes the note on GitHub`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").delete()
        sync.sync(remoteUrl, phone)

        val outcome = sync.resolve(remoteUrl, phone, mapOf("ideas/tyfold.md" to null))

        assertInstanceOf(SyncOutcome.Synced::class.java, outcome)
        assertFalse(GitSync.isMerging(phone))
        assertFalse(File(phone, "ideas/tyfold.md").exists())
        Git.open(computer).use { it.pull().call() }
        assertFalse(File(computer, "ideas/tyfold.md").exists())
    }

    @Test
    fun `choosing the content in a delete conflict brings the note back`() {
        sync.sync(remoteUrl, phone)
        computerEdits("ideas/tyfold.md", "COMPUTER\n")
        File(phone, "ideas/tyfold.md").delete()
        sync.sync(remoteUrl, phone)

        val outcome = sync.resolve(remoteUrl, phone, mapOf("ideas/tyfold.md" to "COMPUTER\n"))

        assertInstanceOf(SyncOutcome.Synced::class.java, outcome)
        assertEquals("COMPUTER\n", File(phone, "ideas/tyfold.md").readText())
        assertEquals("COMPUTER\n", computerPulls("ideas/tyfold.md"))
    }

    private fun computerAddsSymlink(path: String, target: File) = Git.open(computer).use { git ->
        Files.createSymbolicLink(File(computer, path).toPath(), target.toPath())
        git.add().addFilepattern(".").call()
        git.commit().setMessage("note: link").setAuthor("Computer", "c@example.invalid").call()
        git.push().call()
    }

    @Test
    fun `a symlink on GitHub arrives as a plain file holding its target`() {
        val outside = File(tmp, "app-private.txt").apply { writeText("secret") }
        computerAddsSymlink("ideas/link.md", outside)

        sync.sync(remoteUrl, phone)
        val link = File(phone, "ideas/link.md")
        assertFalse(Files.isSymbolicLink(link.toPath()))
        assertEquals(outside.path, link.readText())

        // Git still records it as a symlink, so the next sync has nothing to send.
        assertEquals(SyncOutcome.Synced(), sync.sync(remoteUrl, phone))
    }

    @Test
    fun `an empty repository still clones`() {
        val empty = File(tmp, "empty.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(empty).call().close()
        assertEquals(SyncOutcome.Synced(cloned = true), sync.sync(empty.toURI().toString(), File(tmp, "phone/kb-empty")))
    }

    @Test
    fun `a clone made before symlinks were turned off has its symlinks rewritten`() {
        val outside = File(tmp, "app-private.txt").apply { writeText("secret") }
        computerAddsSymlink("ideas/link.md", outside)
        Git.cloneRepository().setURI(remoteUrl).setDirectory(phone).call().close()
        val link = File(phone, "ideas/link.md").toPath()
        assertTrue(Files.isSymbolicLink(link))

        assertEquals(SyncOutcome.Synced(), sync.sync(remoteUrl, phone))
        assertFalse(Files.isSymbolicLink(link))
        assertEquals("secret", outside.readText())
    }
}
