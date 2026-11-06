package com.cpinto.vademo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The reading and editing workflow on a real device, from the home screen:
 * open a knowledge base, a folder and a note, follow a `[[link]]`, then edit
 * and save. Syncing with GitHub is covered by [GitOnDeviceTest], since this
 * test has no network remote.
 */
@RunWith(AndroidJUnit4::class)
class ReadAndEditTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var kb: File
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun writeKnowledgeBase() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(filesDir, "kb").deleteRecursively()
        // Named like one of the default repositories, so the home screen lists it.
        kb = File(filesDir, "kb/kb-distributed-systems")
        write("index.md", note("Business", "hub", "- [[ideas]]"))
        write("ideas/ideas.md", note("Ideas", "hub", "- [[tyfold]]"))
        write(
            "ideas/tyfold.md",
            note("Tyfold desktop app", "idea", "Scored with the method below.\n\n[[business-idea-scorecard|the scorecard]]\n"),
        )
        write("ideas/business-idea-scorecard.md", note("Business idea scorecard", "concept", "Seven criteria."))
        Git.init().setInitialBranch("main").setDirectory(kb).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("fixture").setAuthor("Test", "test@example.invalid").call()
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun close() {
        scenario?.close()
    }

    @Test
    fun browseReadFollowALinkThenEdit() {
        compose.onNodeWithText("kb-distributed-systems").performClick()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.onNodeWithText("Scored with the method below.").assertIsDisplayed()

        compose.onNodeWithText("the scorecard").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Seven criteria.")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Edit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Seven criteria.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Seven criteria.", substring = true))
            .performTextReplacement(note("Business idea scorecard", "concept", "Eight criteria now."))
        compose.onNodeWithContentDescription("Save").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Eight criteria now.")).fetchSemanticsNodes().isNotEmpty()
        }
        val saved = File(kb, "ideas/business-idea-scorecard.md").readText()
        assertTrue(saved, saved.contains("Eight criteria now."))
        // Saving refreshes the note's updated date, per the knowledge-base convention.
        assertTrue(saved, !saved.contains("updated: 2026-01-01"))
    }

    private fun write(path: String, text: String) {
        File(kb, path).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    private fun note(title: String, type: String, body: String) =
        "---\ntitle: $title\ntype: $type\nstatus: draft\ntags: []\ncreated: 2026-01-01\nupdated: 2026-01-01\n---\n\n$body\n"
}
