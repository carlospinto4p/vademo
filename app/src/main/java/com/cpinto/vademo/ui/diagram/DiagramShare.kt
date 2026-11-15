package com.cpinto.vademo.ui.diagram

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.MermaidExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * tyfold's Export on a phone: instead of a save dialog, Android's share
 * sheet, so the diagram can go to a chat, an email, a file manager or Drive.
 */
internal object DiagramShare {

    /** The diagram as a Mermaid chart in a fenced block, which renders when pasted into GitHub or most Markdown. */
    fun mermaid(context: Context, diagram: Diagram) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, diagram.title)
            putExtra(Intent.EXTRA_TEXT, MermaidExport.markdown(diagram))
        }
        context.startActivity(Intent.createChooser(send, "Share as Mermaid"))
    }

    /** The whole diagram as a PNG named after its file, written to the app's cache and shared from there. */
    suspend fun image(context: Context, name: String, picture: ImageBitmap) {
        val file = withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "exports").apply { mkdirs() }
            // Written whole and then renamed, so nothing ever reads a half-written image.
            val tmp = File(folder, "$name.png.tmp")
            tmp.outputStream().use { picture.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            File(folder, "$name.png").also { tmp.renameTo(it) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share as image"))
    }
}
