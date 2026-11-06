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
        File(phone, "ideas/tyfold.md").delete()

        sync.sync(remoteUrl, phone)

        Git.open(computer).use { it.pull().call() }
        assertTrue(File(computer, "ideas/new-idea.md").isFile)
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
        assertTrue(sync.isMerging(phone))

        val conflict = sync.conflicts(phone).single()
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
        assertFalse(sync.isMerging(phone))
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
        assertTrue(sync.isMerging(phone))
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
}
