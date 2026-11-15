package com.cpinto.vademo.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Forward history, as in a browser: going back from a screen keeps it, so
 * Forward can open it again, and opening anything new forgets it.
 *
 * The navigation library only keeps the way back, so this follows the
 * screen on top and works out from each change whether it was a step back
 * (a screen already on the stack came to the top) or a step to somewhere
 * new. Only places, such as notes, diagrams and folders, are kept: going
 * back from the editor or the settings must not offer to reopen them.
 */
class NavHistory {

    private data class Entry(val id: String, val route: String, val place: Boolean)

    private val stack = mutableListOf<Entry>()

    /** Routes to go forward to; the next one is last. */
    private val forward = mutableStateListOf<String>()

    /** The route [takeForward] handed out, so its arrival does not clear the rest. */
    private var arriving: String? = null

    val canGoForward: Boolean get() = forward.isNotEmpty()

    /** Called with each screen that comes to the top: its back-stack [id], [route], and whether it is a place. */
    fun onTop(id: String, route: String, place: Boolean) {
        val at = stack.indexOfFirst { it.id == id }
        if (at >= 0) {
            val left = stack.subList(at + 1, stack.size)
            left.asReversed().filter { it.place }.forEach { forward.add(it.route) }
            left.clear()
            return
        }
        if (route != arriving) forward.clear()
        arriving = null
        stack.add(Entry(id, route, place))
    }

    /** The route to open for Forward, or null when there is none. */
    fun takeForward(): String? = forward.removeLastOrNull()?.also { arriving = it }

    /** Opens a route; set by the navigation host. */
    var open: (String) -> Unit = {}

    fun goForward() {
        takeForward()?.let(open)
    }
}

/** The app's forward history, for the Forward button in each screen's top bar. */
val LocalNavHistory = staticCompositionLocalOf { NavHistory() }

/** Back and Forward, side by side, for a top bar's navigation icon. */
@Composable
fun BackForward(onBack: () -> Unit) {
    val history = LocalNavHistory.current
    Row {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = history::goForward, enabled = history.canGoForward) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
        }
    }
}
