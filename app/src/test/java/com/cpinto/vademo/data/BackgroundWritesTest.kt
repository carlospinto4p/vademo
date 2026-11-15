package com.cpinto.vademo.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Collections

class BackgroundWritesTest {

    private val file = File("flow.json")

    @Test
    fun `awaiting a file waits until its write has finished`() = runBlocking {
        val writes = BackgroundWrites()
        val release = CompletableDeferred<Unit>()
        val done = mutableListOf<String>()
        writes.launch(file) { release.await(); done += "written" }
        launch(Dispatchers.Default) { delay(100); release.complete(Unit) }
        // Called while the write is still held back, so this really waits.
        withTimeout(5_000) { writes.awaitFor(file) }
        assertEquals(listOf("written"), done)
    }

    @Test
    fun `writes to one file run in the order they were started`() = runBlocking {
        val writes = BackgroundWrites()
        val release = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<Int>())
        writes.launch(file) { release.await(); order += 1 }
        writes.launch(file) { order += 2 }
        release.complete(Unit)
        withTimeout(5_000) { writes.awaitFor(file) }
        assertEquals(listOf(1, 2), order)
    }

    @Test
    fun `a file with nothing pending does not wait`() = runBlocking {
        withTimeout(1_000) { BackgroundWrites().awaitFor(file) }
    }
}
