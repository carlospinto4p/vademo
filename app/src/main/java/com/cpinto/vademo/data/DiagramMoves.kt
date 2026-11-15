package com.cpinto.vademo.data

import android.content.Context
import com.cpinto.vademo.kb.Position
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where boxes were dragged on this phone, per diagram: offsets in dp from
 * where the layout puts them. Kept in the app's own storage, never in the
 * diagram file (tyfold keeps dragged places off the file too) and never in
 * the knowledge base's folder, which sync pushes to GitHub whole.
 */
@Singleton
class DiagramMoves internal constructor(private val root: File) {

    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, "diagram-moves"))

    private val serializer = MapSerializer(String.serializer(), Position.serializer())

    private fun fileFor(kb: String, diagram: String) = File(File(root, kb), "$diagram.json")

    fun read(kb: String, diagram: String): Map<String, Position> =
        runCatching { Json.decodeFromString(serializer, fileFor(kb, diagram).readText()) }.getOrDefault(emptyMap())

    fun write(kb: String, diagram: String, moves: Map<String, Position>) {
        val file = fileFor(kb, diagram)
        if (moves.isEmpty()) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(Json.encodeToString(serializer, moves))
        tmp.renameTo(file)
    }

    fun forget(kb: String, diagram: String) {
        fileFor(kb, diagram).delete()
    }
}
