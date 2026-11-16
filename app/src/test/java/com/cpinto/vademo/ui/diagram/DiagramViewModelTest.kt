package com.cpinto.vademo.ui.diagram

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.cpinto.vademo.data.BackgroundWrites
import com.cpinto.vademo.data.DiagramClipboard
import com.cpinto.vademo.data.DiagramMoves
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.kb.DiagramDocument
import com.cpinto.vademo.kb.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * How [DiagramViewModel] saves, against real files in a temporary folder.
 * The 400 ms save delay runs on a test clock; reading and writing the file
 * run on real threads, so the tests wait for their result on disk.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagramViewModelTest {

    @TempDir
    lateinit var tmp: File

    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val writes = BackgroundWrites()
    private lateinit var moves: DiagramMoves
    private lateinit var file: File

    @BeforeEach
    fun setUp() {
        // Not reset after each test: a read or write still on a real thread
        // when a test ends resumes on Main, which must then still exist.
        Dispatchers.setMain(main)
        moves = DiagramMoves(File(tmp, "diagram-moves"))
        file = File(tmp, "kb/example/.tyfold-graphs/plan.json")
        file.parentFile.mkdirs()
        file.writeText(DiagramDocument.new("Plan").toText())
    }

    @AfterEach
    fun tearDown() {
        store.clear()
        // A save started by closing the screen, finished before the temporary folder is deleted.
        runBlocking { writes.awaitFor(file) }
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun resetMainDispatcher() = Dispatchers.resetMain()
    }

    private fun viewModel(): DiagramViewModel {
        val repository = KbRepository(File(tmp, "kb")) { error("Settings are not read by the diagram screen") }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DiagramViewModel(
                SavedStateHandle(mapOf("kb" to "example", "name" to "plan")),
                repository, moves, DiagramClipboard(), writes,
            ) as T
        }
        return ViewModelProvider(store, factory)[DiagramViewModel::class.java]
    }

    /** Waits on real time for work done on real threads. */
    private suspend fun waitUntil(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    private suspend fun TestScope.opened(): DiagramViewModel = viewModel().also { vm ->
        vm.load()
        runCurrent()
        waitUntil { vm.state.value.editable }
    }

    private fun titleOnDisk() = DiagramDocument.parse(file.readText()).diagram.title

    @Test
    fun `an edit is written 400 ms after it is made, not before`() = runTest(main) {
        val vm = opened()
        vm.edit { it.withTitle("Renamed") }

        advanceTimeBy(399)
        runCurrent()
        assertEquals("Plan", titleOnDisk())

        advanceTimeBy(2)
        waitUntil { titleOnDisk() == "Renamed" }
    }

    @Test
    fun `a second edit within 400 ms starts the wait again and both are written once`() = runTest(main) {
        val vm = opened()
        vm.edit { it.withTitle("First") }
        advanceTimeBy(300)
        vm.edit { it.withTitle("Second") }
        advanceTimeBy(300)
        runCurrent()
        assertEquals("Plan", titleOnDisk())

        advanceTimeBy(101)
        waitUntil { titleOnDisk() == "Second" }
    }

    @Test
    fun `an edit is not written over a file that changed on disk`() = runTest(main) {
        val vm = opened()
        // A sync brings a new version while the diagram is open.
        file.writeText(DiagramDocument.new("From GitHub").toText())
        vm.edit { it.withTitle("Mine") }

        advanceTimeBy(401)
        waitUntil { vm.state.value.message != null }

        assertTrue(vm.state.value.message!!.contains("changed on disk"), vm.state.value.message)
        assertEquals("From GitHub", titleOnDisk())
        assertEquals("From GitHub", vm.state.value.diagram?.title)
        // The history starts over with the file read again.
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `undo keeps the last 100 edits`() = runTest(main) {
        val vm = opened()
        repeat(101) { i -> vm.edit { it.withTitle("Edit $i") } }

        var undone = 0
        while (vm.state.value.canUndo) {
            vm.undo()
            undone++
        }

        assertEquals(100, undone)
        assertEquals("Edit 0", vm.state.value.diagram?.title)
        advanceTimeBy(401)
        waitUntil { titleOnDisk() == "Edit 0" }
    }

    @Test
    fun `an edit still waiting when the screen closes is written`() = runTest(main) {
        val vm = opened()
        vm.edit { it.withTitle("Closed early") }
        advanceTimeBy(100)

        store.clear()
        writes.awaitFor(file)

        assertEquals("Closed early", titleOnDisk())
    }

    @Test
    fun `deleting the diagram is not undone by an edit still waiting to be written`() = runTest(main) {
        moves.write("example", "plan", mapOf("a" to Position(10.0, 10.0)))
        val vm = opened()
        vm.edit { it.withTitle("About to go") }

        vm.deleteDiagram()
        waitUntil { vm.state.value.deleted }
        advanceTimeBy(1_000)
        runCurrent()
        withContext(Dispatchers.Default) { delay(100) }

        assertFalse(file.exists())
        assertEquals(emptyMap<String, Position>(), moves.read("example", "plan"))
    }
}
