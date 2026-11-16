package com.cpinto.vademo

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Editing a diagram on a device, from the home screen, and checking the file
 * on disk after each step: a box renamed, the rename undone, an arrow drawn,
 * and nothing editable while a sync conflict waits.
 */
@RunWith(AndroidJUnit4::class)
class DiagramEditTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var kb: File
    private val diagram get() = File(kb, ".tyfold-graphs/flow.json")
    private var scenario: ActivityScenario<MainActivity>? = null

    private fun flow(client: String) =
        """{ "version": 1, "title": "Request flow", "direction": "LR",
             "nodes": [ { "id": "client", "label": "$client" }, { "id": "server", "label": "Server" } ],
             "edges": [] }"""

    @Before
    fun writeKnowledgeBase() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        File(filesDir, "kb").deleteRecursively()
        kb = File(filesDir, "kb/kb-distributed-systems")
        File(kb, "index.md").apply { parentFile!!.mkdirs() }.writeText("---\ntitle: Index\n---\n\nHome.\n")
        diagram.apply { parentFile!!.mkdirs() }.writeText(flow("Client"))
        Git.init().setInitialBranch("main").setDirectory(kb).call().use { git ->
            git.add().addFilepattern(".").call()
            git.commit().setMessage("fixture").setAuthor("Test", "test@example.invalid").call()
        }
    }

    @After
    fun close() {
        scenario?.close()
    }

    private fun openDiagram() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Diagrams").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Request flow")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Request flow").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Server")).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Waits for the edit to be written; saving happens 400 ms after the last edit. */
    private fun waitForFile(check: (String) -> Boolean) {
        compose.waitUntil(5_000) { check(diagram.readText()) }
    }

    private fun longPress(text: String) {
        compose.onNodeWithText(text).performTouchInput { longClick() }
    }

    @Test
    fun renameABoxThenUndo() {
        openDiagram()
        longPress("Client")
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasSetTextAction() and hasText("Client")).performTextReplacement("Browser")
        compose.onNodeWithText("Save").performClick()
        waitForFile { "\"label\": \"Browser\"" in it }

        compose.onNodeWithContentDescription("Undo").performClick()
        waitForFile { "\"label\": \"Client\"" in it }
        val text = diagram.readText()
        // Written as tyfold writes: two-space indent and a final newline.
        assertTrue(text, text.startsWith("{\n  \"version\": 1,") && text.endsWith("}\n"))
    }

    @Test
    fun leavingRightAfterAnEditStillSavesIt() {
        openDiagram()
        longPress("Client")
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasSetTextAction() and hasText("Client")).performTextReplacement("Browser")
        compose.onNodeWithText("Save").performClick()
        // Back before the 400 ms save delay runs out: the edit is written as the screen closes.
        compose.onNodeWithContentDescription("Back").performClick()
        waitForFile { "\"label\": \"Browser\"" in it }
        // Opened again, it shows the saved edit.
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Request flow")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Request flow").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Browser")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun drawAnArrowBetweenTwoBoxes() {
        openDiagram()
        longPress("Client")
        compose.onNodeWithText("Connect to…").performClick()
        compose.onNodeWithText("Tap the box the arrow goes to.").fetchSemanticsNode()
        compose.onNodeWithText("Server").performClick()
        waitForFile { "\"source\": \"client\"" in it && "\"target\": \"server\"" in it }
    }

    @Test
    fun nothingIsSavedOnceAConflictWaits() {
        openDiagram()
        conflictInTheIndex()
        val before = diagram.readText()
        longPress("Client")
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasSetTextAction() and hasText("Client")).performTextReplacement("Browser")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Editing is off", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(before, diagram.readText())
        assertTrue(compose.onAllNodes(hasContentDescription("Undo")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun createADiagramFromTheList() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.openKnowledgeBase()
        compose.onNodeWithContentDescription("Diagrams").performClick()
        // The button appears once the list has checked that no sync conflict waits.
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("New diagram")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("New diagram").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Write path")
        compose.onNodeWithText("Top to bottom").performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("This diagram has no boxes yet", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val created = File(kb, ".tyfold-graphs/write-path.json").readText()
        assertTrue(created, "\"title\": \"Write path\"" in created && "\"direction\": \"TB\"" in created)
    }

    @Test
    fun duplicateABox() {
        openDiagram()
        longPress("Server")
        compose.onNodeWithText("Duplicate").performClick()
        waitForFile { "\"id\": \"node-1\"" in it }
        // The copy comes back selected, ready to be dragged away from the original.
        compose.onNodeWithText("1 selected").fetchSemanticsNode()
    }

    @Test
    fun setTheStatusOfOneBoxFromItsMenu() {
        // Nothing is selected here: the editor must act on the box it was opened for.
        openDiagram()
        longPress("Server")
        compose.onNodeWithText("Status…").performClick()
        compose.onNodeWithText("changed").performClick()
        waitForFile { "\"status\": \"changed\"" in it }
        assertEquals(1, Regex("\"status\"").findAll(diagram.readText()).count())
    }

    @Test
    fun setTheStatusOfSelectedBoxesThenTheSelectionEnds() {
        openDiagram()
        longPress("Server")
        compose.onNodeWithText("Select (to act on several, or drag)").performClick()
        compose.onNodeWithText("Client").performClick()
        compose.onNodeWithText("2 selected").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Actions on selected").performClick()
        compose.onNodeWithText("Status…").performClick()
        compose.onNodeWithText("added").performClick()
        waitForFile { Regex("\"status\": \"added\"").findAll(it).count() == 2 }
        assertTrue(compose.onAllNodes(hasText("2 selected")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun draggingKeepsThePlaceOnThePhoneAndOutOfTheFile() {
        openDiagram()
        val before = diagram.readText()
        longPress("Server")
        compose.onNodeWithText("Select (to act on several, or drag)").performClick()
        compose.onNodeWithText("Server").performTouchInput { swipe(center, center + Offset(0f, 300f), 600) }
        val moves = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "diagram-moves/kb-distributed-systems/flow.json")
        compose.waitUntil(5_000) { moves.isFile && "server" in moves.readText() }
        assertEquals(before, diagram.readText())
        assertTrue(kb.walkTopDown().none { it.name.contains("moves") })
    }

    @Test
    fun deleteADiagram() {
        openDiagram()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Delete diagram").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("No diagrams yet", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(!diagram.exists())
    }

    @Test
    fun shareAsImageWritesAPictureOfTheWholeDiagram() {
        openDiagram()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Share as image").performClick()
        val png = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "exports/flow.png")
        compose.waitUntil(5_000) { png.isFile && png.length() > 0 }
        val bytes = png.readBytes()
        // The PNG signature, then a picture as wide as two boxes side by side at least.
        assertTrue(bytes.take(4) == listOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        val image = android.graphics.BitmapFactory.decodeFile(png.path)
        assertTrue("${image.width} px wide", image.width > 400)
    }

    /** Leaves the knowledge base in git's merging state, with a conflict in a note, so the diagram itself still reads. */
    private fun conflictInTheIndex() {
        val index = File(kb, "index.md")
        Git.open(kb).use { git ->
            git.branchCreate().setName("github").call()
            index.writeText("---\ntitle: Index\n---\n\nPhone.\n")
            git.commit().setAll(true).setMessage("phone").setAuthor("Test", "test@example.invalid").call()
            git.checkout().setName("github").call()
            index.writeText("---\ntitle: Index\n---\n\nGitHub.\n")
            git.commit().setAll(true).setMessage("github").setAuthor("Test", "test@example.invalid").call()
            git.checkout().setName("main").call()
            val merge = git.merge().include(git.repository.resolve("github")).call()
            assertEquals(MergeResult.MergeStatus.CONFLICTING, merge.mergeStatus)
        }
    }
}
