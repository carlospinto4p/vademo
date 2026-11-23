package com.cpinto.vademo.ui.note

import androidx.compose.material3.Text
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpinto.vademo.data.KbRepository
import com.cpinto.vademo.data.SettingsRepository
import com.cpinto.vademo.data.TextZoom
import com.cpinto.vademo.kb.DiagramFile
import com.cpinto.vademo.kb.Frontmatter
import com.cpinto.vademo.kb.FrontmatterParser
import com.cpinto.vademo.kb.IndexedNote
import com.cpinto.vademo.kb.NoteIndex
import com.cpinto.vademo.kb.WikiLinks
import com.cpinto.vademo.kb.readDiagram
import com.mikepenz.markdown.m3.Markdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** A run of the note's text ready to render, or a diagram it embeds with `![[name]]`. */
sealed interface NoteBlock {
    data class Text(val markdown: String) : NoteBlock
    /** [file] is null when the knowledge base has no diagram named [name]. */
    data class Diagram(val name: String, val file: DiagramFile?) : NoteBlock
}

data class NoteState(
    val title: String = "",
    val frontmatter: Frontmatter? = null,
    val blocks: List<NoteBlock> = emptyList(),
    val missing: Boolean = false,
    /** Notes linking to this one; filled in after the text, since it reads every note. */
    val linkedFrom: List<IndexedNote> = emptyList(),
    /** Notes this one links to that exist, each once; filled in with [linkedFrom]. */
    val linksTo: List<IndexedNote> = emptyList(),
    /** The hub this note sits under, shown under its title; null for the index or a topic with no hub. */
    val hub: IndexedNote? = null,
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: KbRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val kbName: String = checkNotNull(savedState["kb"])
    val path: String = checkNotNull(savedState["path"])
    private val kb = repository.knowledgeBase(kbName)

    private val _state = MutableStateFlow(NoteState())
    val state: StateFlow<NoteState> = _state

    /** Kept here rather than in the DataStore so a pinch redraws at once. */
    private val _textScale = MutableStateFlow(TextZoom.DEFAULT)
    val textScale: StateFlow<Float> = _textScale

    fun zoomBy(factor: Float) {
        _textScale.value = TextZoom.zoomed(_textScale.value, factor)
    }

    /** Called when the fingers lift, so a pinch is one write and not sixty. */
    fun saveTextScale() {
        viewModelScope.launch { settings.saveTextScale(_textScale.value) }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _textScale.value = TextZoom.clamp(settings.textScale())
            val file = kb.fileAt(path)
            if (!file.isFile) {
                _state.value = NoteState(title = path, missing = true)
                return@launch
            }
            settings.noteOpened(kbName, path)
            val parsed = FrontmatterParser.parse(file.readText())
            val blocks = WikiLinks.parts(parsed.body).map { part ->
                when (part) {
                    is WikiLinks.NotePart.Markdown -> NoteBlock.Text(WikiLinks.toMarkdown(part.text))
                    is WikiLinks.NotePart.Embed -> NoteBlock.Diagram(
                        part.name,
                        kb.diagramFile(part.name)?.takeIf { it.isFile }?.let(::readDiagram),
                    )
                }
            }
            _state.value = NoteState(
                title = parsed.frontmatter?.title ?: file.nameWithoutExtension,
                frontmatter = parsed.frontmatter,
                blocks = blocks,
            )
            val noteIndex = repository.index(kbName)
            index = noteIndex
            _state.value = _state.value.copy(
                linkedFrom = noteIndex.linkedFrom(path),
                linksTo = noteIndex.linksTo(path),
                hub = noteIndex.hubOf(path),
            )
        }
    }

    /** The knowledge base's notes, once [load] has read them; links are resolved from it. */
    @Volatile private var index: NoteIndex? = null

    /**
     * Path of the note a `[[target]]` link points at, or `null` when no such
     * note exists. Looked up in the index; only a note it does not hold (one
     * at the repository's root, or a tap before the index is ready) is
     * searched for on disk, off the main thread.
     */
    suspend fun resolve(target: String): String? =
        index?.note(target)?.path
            ?: withContext(Dispatchers.IO) { kb.resolve(target)?.let(kb::relativePath) }
}
