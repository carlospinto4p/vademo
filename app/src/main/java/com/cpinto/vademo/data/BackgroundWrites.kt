package com.cpinto.vademo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes that must finish after the screen that started them has closed,
 * such as a diagram's last edit when the user leaves it. They run off the
 * main thread, on a scope that lives as long as the app.
 *
 * A screen that opens the same file again calls [awaitFor] before reading
 * it, so it never shows the version from before the write.
 */
@Singleton
class BackgroundWrites @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = mutableMapOf<File, Job>()

    fun launch(file: File, write: suspend () -> Unit) {
        synchronized(pending) {
            val before = pending[file]
            val job = scope.launch {
                before?.join()
                write()
            }
            pending[file] = job
            job.invokeOnCompletion { synchronized(pending) { if (pending[file] === job) pending.remove(file) } }
        }
    }

    suspend fun awaitFor(file: File) {
        synchronized(pending) { pending[file] }?.join()
    }
}
