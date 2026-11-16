package com.cpinto.vademo.kb

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Writes this file whole into [tmp], then moves it into place in one step,
 * so nothing reads half of it: not the app, and not sync's `git add`. A
 * failed write leaves the old content. [tmp] must be on the same file system.
 */
fun File.writeAtomically(tmp: File = File(parentFile, "$name.tmp"), write: (OutputStream) -> Unit) {
    parentFile?.mkdirs()
    tmp.parentFile?.mkdirs()
    try {
        tmp.outputStream().use(write)
        Files.move(tmp.toPath(), toPath(), StandardCopyOption.ATOMIC_MOVE)
    } finally {
        tmp.delete()
    }
}
