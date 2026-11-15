package com.cpinto.vademo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cpinto.vademo.git.Author
import com.cpinto.vademo.git.GitSync
import com.cpinto.vademo.git.SyncOutcome
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.FileMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files

/**
 * JGit is a Java library that was not written for Android's runtime, so the
 * JVM tests cannot prove it works on a phone. This runs the clone, edit and
 * push cycle on the device itself, against a repository in app storage.
 */
@RunWith(AndroidJUnit4::class)
class GitOnDeviceTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "git-on-device")
        root.deleteRecursively()
        root.mkdirs()
    }

    @Test
    fun cloneEditAndPushOnTheDevice() {
        val bare = File(root, "remote.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close()
        val seed = File(root, "seed")
        Git.init().setInitialBranch("main").setDirectory(seed).call().use { git ->
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(bare.toURI().toString())).call()
            File(seed, "note.md").writeText("from seed\n")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("seed").setAuthor("Seed", "seed@example.invalid").call()
            git.push().setRemote("origin").add("main").call()
        }

        val sync = GitSync(credentials = null, author = Author("Device", "device@example.invalid"))
        val phone = File(root, "phone")
        val url = bare.toURI().toString()

        assertEquals(SyncOutcome.Synced(cloned = true), sync.sync(url, phone))
        File(phone, "note.md").writeText("edited on device\n")

        val outcome = sync.sync(url, phone)
        assertTrue("sync failed: $outcome", outcome is SyncOutcome.Synced)
        assertEquals(1, (outcome as SyncOutcome.Synced).pushed)

        Git.open(bare).use { git ->
            assertEquals("note: edit note from phone", git.log().setMaxCount(1).call().single().fullMessage)
        }
    }

    /** A symlink on GitHub must not become a real one on the phone, where it could reach app files. */
    @Test
    fun aSymlinkArrivesAsAPlainFile() {
        val bare = File(root, "remote.git")
        Git.init().setBare(true).setInitialBranch("main").setDirectory(bare).call().close()
        val seed = File(root, "seed")
        val target = File(root, "private.txt").apply { writeText("secret") }
        Git.init().setInitialBranch("main").setDirectory(seed).call().use { git ->
            git.remoteAdd().setName("origin").setUri(org.eclipse.jgit.transport.URIish(bare.toURI().toString())).call()
            Files.createSymbolicLink(File(seed, "link.md").toPath(), target.toPath())
            git.add().addFilepattern(".").call()
            git.commit().setMessage("seed").setAuthor("Seed", "seed@example.invalid").call()
            git.push().setRemote("origin").add("main").call()
            // JGit on Android must record it as a symlink, or this test proves nothing.
            assertEquals(FileMode.SYMLINK, git.repository.readDirCache().getEntry("link.md").fileMode)
        }

        val sync = GitSync(credentials = null, author = Author("Device", "device@example.invalid"))
        val phone = File(root, "phone")
        sync.sync(bare.toURI().toString(), phone)

        val link = File(phone, "link.md")
        assertFalse(Files.isSymbolicLink(link.toPath()))
        assertEquals(target.path, link.readText())
    }
}
