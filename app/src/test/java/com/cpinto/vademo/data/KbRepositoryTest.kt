package com.cpinto.vademo.data

import com.cpinto.vademo.git.SyncOutcome
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** [KbRepository] over real repositories in a temporary folder, with the settings held in a field. */
class KbRepositoryTest {

    @TempDir
    lateinit var tmp: File

    private lateinit var root: File
    private lateinit var repository: KbRepository
    private var settings = Settings(token = "", authorName = "Phone", authorEmail = "phone@example.invalid", repoUrls = emptyList())

    @BeforeEach
    fun setUp() {
        root = File(tmp, "kb")
        repository = KbRepository(root) { settings }
    }

    /** A repository standing in for GitHub at `<owner>/<name>.git`, holding one note. */
    private fun github(owner: String, name: String, note: String = "from $owner\n"): String {
        val bare = File(tmp, "$owner/$name.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close()
        val work = File(tmp, "work-$owner-$name")
        Git.init().setInitialBranch("main").setDirectory(work).call().use { git ->
            File(work, "note.md").writeText(note)
            git.add().addFilepattern(".").call()
            git.commit().setMessage("note: first").setAuthor("Computer", "c@example.invalid").call()
            git.remoteAdd().setName("origin").setUri(URIish(bare.toURI().toString())).call()
            git.push().setRemote("origin").add("main").call()
        }
        return bare.toURI().toString()
    }

    private fun use(vararg urls: String) {
        settings = settings.copy(repoUrls = urls.toList())
    }

    @Test
    fun `list shows a knowledge base not downloaded yet, then downloaded with its edits`() = runBlocking {
        val url = github("owner", "kb-notes")
        use(url)
        assertEquals(listOf(KbInfo("kb-notes", url, cloned = false, unsyncedEdits = 0, needsChoice = false)), repository.list())

        repository.syncAll()
        File(root, "kb-notes/note.md").writeText("edited on phone\n")

        assertEquals(listOf(KbInfo("kb-notes", url, cloned = true, unsyncedEdits = 1, needsChoice = false)), repository.list())
    }

    @Test
    fun `downloaded lists only folders holding a finished download`() = runBlocking {
        use(github("owner", "kb-notes"), github("owner", "kb-other"))
        repository.syncAll()
        // A folder with no .git, as list() calls missing.
        File(root, "kb-other/.git").deleteRecursively()

        assertEquals(listOf("kb-notes"), repository.downloaded().map { it.name })
    }

    @Test
    fun `two addresses ending in the same name are synced once, by the first`() = runBlocking {
        val first = github("owner", "kb-notes", note = "first\n")
        use(first, github("someone-else", "kb-notes", note = "second\n"))

        repository.syncAll()

        assertEquals(mapOf("kb-notes" to SyncState.Done(SyncOutcome.Synced(cloned = true))), repository.syncStates.value)
        assertEquals("first\n", File(root, "kb-notes/note.md").readText())
    }

    @Test
    fun `changing an address to another repository with the same name stops sync and names the old one`() =
        runBlocking {
            val old = github("owner", "kb-notes", note = "old\n")
            use(old)
            repository.syncAll()

            use(github("someone-else", "kb-notes", note = "new\n"))
            repository.syncAll()

            val state = repository.syncStates.value.getValue("kb-notes") as SyncState.Done
            assertInstanceOf(SyncOutcome.Failed::class.java, state.outcome)
            // JGit stores a file: address as file:///, so look for the path, not the whole address.
            assertTrue((state.outcome as SyncOutcome.Failed).message.contains("/owner/kb-notes.git"), state.outcome.toString())
            assertEquals("old\n", File(root, "kb-notes/note.md").readText())
        }

    @Test
    fun `resolving a knowledge base no longer in the settings fails`() = runBlocking {
        use(github("owner", "kb-notes"))

        assertEquals(
            SyncOutcome.Failed("kb-gone is no longer in Settings"),
            repository.resolve("kb-gone", emptyMap()),
        )
    }
}
