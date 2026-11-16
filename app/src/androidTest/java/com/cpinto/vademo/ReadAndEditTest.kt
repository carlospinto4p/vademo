package com.cpinto.vademo

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cpinto.vademo.data.SettingsRepository
import kotlinx.coroutines.runBlocking
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filesDir = context.filesDir
        File(filesDir, "kb").deleteRecursively()
        // Saved Settings could hide the default knowledge base, and a search
        // saved by an earlier failed run would let the saved-search test pass early.
        runBlocking { SettingsRepository(context).clear() }
        // Named like one of the default repositories, so the home screen lists it.
        kb = File(filesDir, "kb/kb-distributed-systems")
        write("index.md", note("Business", "hub", "- [[ideas]]"))
        write("ideas/ideas.md", note("Ideas", "hub", "- [[tyfold]]"))
        write(
            "ideas/tyfold.md",
            note(
                "Tyfold desktop app",
                "idea",
                "Scored with the method below.\n\n[[business-idea-scorecard|the scorecard]]\n\n![[scoring]]\n",
            ),
        )
        write(
            "ideas/business-idea-scorecard.md",
            note("Business idea scorecard", "concept", "Seven criteria.", tags = "[scoring]"),
        )
        write(
            ".tyfold-graphs/scoring.json",
            """{ "title": "How an idea is scored", "direction": "LR",
                 "nodes": [ { "id": "idea", "label": "An idea" },
                            { "id": "card", "label": "The scorecard", "ref": { "path": "business-idea-scorecard.md" } } ],
                 "edges": [ { "source": "idea", "target": "card", "label": "scored by" } ] }""",
        )
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
        compose.openKnowledgeBase()
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

    @Test
    fun aTableGivesItsLongColumnTheWidth() {
        val reasons = "Plenty of people already pay for something close to this, and the " +
            "cost of building a first version is a few weekends of work at most."
        write(
            "ideas/scores.md",
            note("Scores", "concept", "| Criterion | Score | Reasons |\n|---|---|---|\n| Demand | four | $reasons |\n"),
        )
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Scores").performClick()
        // A cell's text keeps the spaces around it in the markdown.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Demand", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val score = compose.onNodeWithText("four", substring = true).fetchSemanticsNode().boundsInRoot
        val reason = compose.onNodeWithText(reasons, substring = true).fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        // The reasons wrap inside the screen instead of running off it. Equal
        // columns left their text less than a third of the screen, padding
        // taken off; sized by content they get more.
        assertTrue("reasons $reason, screen $screen", reason.right <= screen.right)
        assertTrue("reasons $reason, screen $screen", reason.width > screen.width / 3)
        assertTrue("score $score, reasons $reason", score.right < reason.left)
    }

    @Test
    fun startANoteFromATemplateThenSaveIt() {
        write("templates/note.md", "---\ntitle: <Title>\ntype: concept\nstatus: draft\ntags: []\ncreated: YYYY-MM-DD\nupdated: YYYY-MM-DD\n---\n\n# <Title>\n\nHub: [[<topic-hub>]]\n\n<One idea.>\n")
        write("templates/question.md", "---\ntitle: <The question>\ntype: question\n---\n\n# <The question>\n")
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("New note", useUnmergedTree = true).performClick()

        // A name another note already has is refused, wherever that note is.
        compose.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Tyfold")
        compose.onNode(hasSetTextAction() and hasText("tyfold")).assertIsDisplayed()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("There is already a note named tyfold", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNode(hasSetTextAction() and hasText("Tyfold")).performTextReplacement("Pricing: one-off or yearly")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("New note")).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(hasText("# Pricing: one-off or yearly", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        // Nothing is written until Save.
        val file = File(kb, "ideas/pricing-one-off-or-yearly.md")
        assertTrue(!file.exists())
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Pricing: one-off or yearly")).fetchSemanticsNodes().isNotEmpty()
        }

        val saved = file.readText()
        val today = java.time.LocalDate.now().toString()
        assertTrue(saved, saved.contains("title: \"Pricing: one-off or yearly\""))
        assertTrue(saved, saved.contains("created: $today") && saved.contains("updated: $today"))
        assertTrue(saved, saved.contains("Hub: [[ideas]]"))
    }

    @Test
    fun leavingANewNoteUnsavedWritesNothing() {
        write("templates/note.md", "---\ntitle: <Title>\n---\n\n# <Title>\n")
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("New note", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction() and hasText("Title")).performTextInput("Scratch")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("# Scratch", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Tyfold desktop app")).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!File(kb, "ideas/scratch.md").exists())
    }

    @Test
    fun goBackThenForwardAgain() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Forward").assertIsNotEnabled()

        // From a note, by a link, then back and forward.
        compose.onNodeWithText("the scorecard").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Seven criteria.")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Forward").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Seven criteria.")).fetchSemanticsNodes().isNotEmpty() }

        // From a note to the diagram it embeds, back to the note, forward to the diagram.
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNode(hasContentDescription("Diagram: How an idea is scored")).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("scored by")).fetchSemanticsNodes().isNotEmpty() }
        // Opening something new forgot the scorecard as the way forward.
        compose.onNodeWithContentDescription("Forward").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Forward").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("scored by")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun openANoteFromTheMap() {
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Map of notes").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Tyfold desktop app")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun openANoteFromADiagram() {
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Diagrams").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("How an idea is scored")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("How an idea is scored").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("The scorecard")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("scored by").assertIsDisplayed()
        compose.onNodeWithText("The scorecard").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Seven criteria.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aNoteShowsTheDiagramItEmbeds() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Diagram: How an idea is scored")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasContentDescription("Diagram: How an idea is scored")).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("scored by")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("The scorecard").assertIsDisplayed()
    }

    @Test
    fun followABacklinkToTheNoteLinkingHere() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Business idea scorecard").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Linked from (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tyfold desktop app").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun openANoteFromTheTypesTab() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("Types").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("idea (1)")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aTagInTheTagsTabListsItsNotes() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("Tags").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("scoring")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("scoring").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Business idea scorecard")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).assert(hasText("kb:distributed-systems tag:scoring"))
        compose.onNodeWithText("Tyfold desktop app").assertDoesNotExist()
    }

    @Test
    fun aHealthRowListsTheNotesItCounts() {
        write("inbox/read-later.md", "A link to look at.")
        compose.openKnowledgeBase()
        compose.onNodeWithText("Health").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Waiting in the inbox")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Waiting in the inbox").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("read-later")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).assert(hasText("kb:distributed-systems folder:inbox"))
        compose.onNodeWithText("Tyfold desktop app").assertDoesNotExist()
    }

    @Test
    fun aNoteTypeChipListsTheNotesOfThatType() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
        // The type chip, labelled with the type alone.
        compose.onNodeWithText("idea").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("kb:distributed-systems type:idea")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Tyfold desktop app").assertIsDisplayed()
        compose.onNodeWithText("Business idea scorecard").assertDoesNotExist()
    }

    @Test
    fun searchFromTheHomeScreenAndOpenANote() {
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("criteria")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Business idea scorecard")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Business idea scorecard").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Seven criteria.")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun searchAKnowledgeBaseWithAFilter() {
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Search").performClick()
        // The field starts with the knowledge base's own filter.
        compose.onNode(hasSetTextAction()).assert(hasText("kb:distributed-systems ", substring = true))
        compose.onNode(hasSetTextAction()).performTextInput("type:idea")
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Tyfold desktop app")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Business idea scorecard").assertDoesNotExist()
    }

    @Test
    fun saveASearchRunItAgainThenDeleteIt() {
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Search").performClick()
        // Before anything is typed, the Health checks are offered as saved searches.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Open questions")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).performTextInput("tag:scoring")
        compose.onNodeWithContentDescription("Save search").performClick()
        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Scoring notes")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scoring notes")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Scoring notes").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Business idea scorecard")).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithContentDescription("Delete Scoring notes").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scoring notes")).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun editingANoteDeletedMeanwhileSaysSoInsteadOfCrashing() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.")).fetchSemanticsNodes().isNotEmpty()
        }
        // A sync removed it while it was on screen.
        File(kb, "ideas/tyfold.md").delete()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("This note no longer exists", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
    }

    @Test
    fun aConflictThatAppearsWhileEditingStopsTheSave() {
        compose.openKnowledgeBase()
        compose.onNodeWithText("ideas").performClick()
        compose.onNodeWithText("Tyfold desktop app").performClick()
        compose.onNodeWithContentDescription("Edit").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Scored with the method below.", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("Scored with the method below.", substring = true))
            .performTextReplacement(note("Tyfold desktop app", "idea", "Edited while a sync ran."))
        // A sync from Home stopped on a conflict in another note meanwhile.
        conflictInTheIndex()
        compose.onNodeWithContentDescription("Save").performClick()

        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Not saved", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val onDisk = File(kb, "ideas/tyfold.md").readText()
        assertTrue(onDisk, !onDisk.contains("Edited while a sync ran."))
        // The edit stays on screen, so it is not lost.
        compose.onNode(hasText("Edited while a sync ran.", substring = true)).assertIsDisplayed()
    }

    /** Leaves the knowledge base in git's merging state, with a conflict in `index.md`. */
    private fun conflictInTheIndex() {
        val index = File(kb, "index.md")
        Git.open(kb).use { git ->
            git.branchCreate().setName("github").call()
            index.writeText(note("Business", "hub", "Phone."))
            git.commit().setAll(true).setMessage("phone").setAuthor("Test", "test@example.invalid").call()
            git.checkout().setName("github").call()
            index.writeText(note("Business", "hub", "GitHub."))
            git.commit().setAll(true).setMessage("github").setAuthor("Test", "test@example.invalid").call()
            git.checkout().setName("main").call()
            git.merge().include(git.repository.resolve("github")).call()
        }
    }

    private fun write(path: String, text: String) {
        File(kb, path).apply { parentFile!!.mkdirs() }.writeText(text)
    }

    private fun note(title: String, type: String, body: String, tags: String = "[]") =
        "---\ntitle: $title\ntype: $type\nstatus: draft\ntags: $tags\ncreated: 2026-01-01\nupdated: 2026-01-01\n---\n\n$body\n"
}
