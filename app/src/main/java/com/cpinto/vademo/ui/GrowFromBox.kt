package com.cpinto.vademo.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** A rectangle on the screen, in pixels from the top left of the app's window. */
data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * The geometry of a note growing out of the box it was opened from, Material's
 * container transform: the note's screen is drawn shrunk to the box's width,
 * at the box's place, cut to the box's height, and grows to fill the screen.
 * Shrunk by width alone, so the note's text keeps its shape and is never
 * squashed; its top shows first, where the title is.
 */
internal object GrowFrom {

    /** How much of the way the note takes to fade in, so it appears over the box rather than at once. */
    const val FADE = 0.3f

    /**
     * At [t] of the way, 0 at the box and 1 at the whole screen: the scale the
     * screen is drawn at, where its top left goes (relative to the screen's
     * own), how much of its height shows and the corners' radius (both in the
     * screen's unscaled pixels), and how opaque it is.
     */
    data class Frame(val scale: Float, val x: Float, val y: Float, val shownHeight: Float, val corner: Float, val alpha: Float)

    fun frame(box: Bounds, screen: Bounds, t: Float, boxCorner: Float): Frame {
        val left = lerp(box.left, screen.left, t)
        val top = lerp(box.top, screen.top, t)
        val width = lerp(box.width, screen.width, t)
        val height = lerp(box.height, screen.height, t)
        val scale = (width / screen.width).coerceAtLeast(MIN_SCALE)
        return Frame(
            scale = scale,
            x = left - screen.left,
            y = top - screen.top,
            shownHeight = (height / scale).coerceAtMost(screen.height),
            corner = lerp(boxCorner, 0f, t) / scale,
            alpha = (t / FADE).coerceIn(0f, 1f),
        )
    }

    /** Whether [box] is big enough on screen to grow from; a box scrolled almost out of sight is not. */
    fun usable(box: Bounds): Boolean = box.width >= MIN_SIDE && box.height >= MIN_SIDE

    private const val MIN_SCALE = 0.01f
    private const val MIN_SIDE = 4f

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/**
 * Where the boxes of a map or a diagram are drawn, so the one tapped can be
 * where its note grows from. Read at the tap, so it is where the box is drawn
 * then, inside any pan and zoom: the zoom is a `graphicsLayer` scale, which a
 * box's own size does not show but its bounds on the screen do.
 */
class BoxBounds {
    private val placed = HashMap<Any, LayoutCoordinates>()

    /** Follows the box known by [key]: goes on its modifier, after its offset and size. */
    fun track(key: Any): Modifier = Modifier.onPlaced { placed[key] = it }

    /** Where the box known by [key] is drawn now, or null when it is not on screen. */
    fun of(key: Any): Bounds? {
        val coordinates = placed[key]?.takeIf { it.isAttached } ?: return null
        val rect = coordinates.boundsInRoot()
        return Bounds(rect.left, rect.top, rect.width, rect.height).takeIf(GrowFrom::usable)
    }
}

@Composable
fun rememberBoxBounds(): BoxBounds = remember { BoxBounds() }

/**
 * Which notes grew out of a box, and from where, so the navigation host can
 * leave their screen change to [growFrom] instead of sliding, going in and
 * coming back. A box's tap sets [from] just before it opens the note; the
 * host [bind]s it to the screen that opens next, if that is a note.
 */
class NoteOpenings {
    private var pending: Bounds? = null
    private val byEntry = HashMap<String, Bounds>()

    /** The box the note about to open is tapped from. */
    fun from(bounds: Bounds?) {
        pending = bounds
    }

    /**
     * Called as the screen [entryId] comes in: takes the box last tapped, if
     * any, for it when it [isNote], and forgets the box either way, so one
     * tapped without opening a note never carries over to a later one.
     * Returns the box [entryId] grows from, also when asked again.
     */
    fun bind(entryId: String, isNote: Boolean): Bounds? {
        pending?.let { if (isNote) byEntry.putIfAbsent(entryId, it) }
        pending = null
        return byEntry[entryId]
    }

    /** The box the screen [entryId] grew from, or null. */
    fun of(entryId: String): Bounds? = byEntry[entryId]

    /** Once [entryId] is gone for good. */
    fun forget(entryId: String) {
        byEntry.remove(entryId)
    }
}

/** The app's [NoteOpenings], set by the navigation host. */
val LocalNoteOpenings = staticCompositionLocalOf { NoteOpenings() }

/**
 * Grows this screen out of the box [box] gives as it comes in, and shrinks it
 * back into that box on its way out, following the screen's own enter and
 * exit; does nothing while [box] gives null. Read only while drawing, so a
 * box bound once the screen is already composed still counts. [duration] is
 * the navigation host's.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun Modifier.growFrom(box: () -> Bounds?, scope: AnimatedVisibilityScope, duration: Int): Modifier {
    val progress by scope.transition.animateFloat(
        transitionSpec = { tween(duration, easing = FastOutSlowInEasing) },
        label = "grow from box",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val corner = with(LocalDensity.current) { BOX_CORNER.toPx() }
    val screen = remember { arrayOfNulls<Bounds>(1) }
    return this
        .onPlaced {
            val at = it.positionInRoot()
            screen[0] = Bounds(at.x, at.y, it.size.width.toFloat(), it.size.height.toFloat())
        }
        .graphicsLayer {
            val t = progress
            if (t >= 1f) return@graphicsLayer
            val from = box() ?: return@graphicsLayer
            val whole = screen[0] ?: return@graphicsLayer
            if (whole.width <= 0f || whole.height <= 0f) return@graphicsLayer
            val frame = GrowFrom.frame(from, whole, t, corner)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = frame.scale
            scaleY = frame.scale
            translationX = frame.x
            translationY = frame.y
            alpha = frame.alpha
            clip = true
            shape = GenericShape { size, _ ->
                addRoundRect(RoundRect(0f, 0f, size.width, frame.shownHeight, CornerRadius(frame.corner)))
            }
        }
}

/** About the corners of the map's and diagrams' boxes, 8 to 12 dp. */
private val BOX_CORNER = 10.dp
