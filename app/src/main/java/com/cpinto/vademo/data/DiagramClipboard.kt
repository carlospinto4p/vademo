package com.cpinto.vademo.data

import com.cpinto.vademo.kb.DiagramClip
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What was last cut or copied from a diagram, for pasting into this diagram
 * or another. Kept in the app, not on the system clipboard, as tyfold keeps
 * it in its window, so copied text elsewhere is left alone.
 */
@Singleton
class DiagramClipboard @Inject constructor() {
    var clip: DiagramClip? = null
}
