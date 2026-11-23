package com.cpinto.vademo.ui.kb

/** The three ways into a knowledge base, along the bottom of its first screen. */
enum class KbTab(val label: String) {
    Map("Map"),
    Diagrams("Diagrams"),
    Notes("Notes");

    companion object {
        /** The tab named [name], or null for an unknown or missing name. */
        fun parse(name: String?): KbTab? = entries.firstOrNull { it.name == name }
    }
}

/** How a knowledge base opens: on [tab], with [diagram] as its main diagram, or with none. */
data class KbStart(val kb: String, val tab: KbTab, val diagram: String?)

/**
 * The tab a knowledge base opens on: the one it was [saved] as left on, else
 * a picture: its main diagram when it has one, else the map of notes.
 */
internal fun firstTab(saved: KbTab?, mainDiagram: String?): KbTab =
    saved ?: if (mainDiagram != null) KbTab.Diagrams else KbTab.Map
