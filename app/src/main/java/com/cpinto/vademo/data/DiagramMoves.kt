package com.cpinto.vademo.data

import android.content.Context
import com.cpinto.vademo.kb.Position
import com.cpinto.vademo.kb.writeAtomically
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
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

    /** The saved places, or none when nothing was dragged or the file cannot be read. */
    fun read(kb: String, diagram: String): Map<String, Position> = try {
        Json.decodeFromString(serializer, fileFor(kb, diagram).readText())
    } catch (e: IOException) {
        emptyMap()
    } catch (e: IllegalArgumentException) {
        // What kotlinx.serialization throws for a file that is not a map of places.
        emptyMap()
    }

    fun write(kb: String, diagram: String, moves: Map<String, Position>) {
        val file = fileFor(kb, diagram)
        if (moves.isEmpty()) {
            file.delete()
            return
        }
        file.writeAtomically { it.write(Json.encodeToString(serializer, moves).toByteArray()) }
    }

    fun forget(kb: String, diagram: String) {
        fileFor(kb, diagram).delete()
    }
}
