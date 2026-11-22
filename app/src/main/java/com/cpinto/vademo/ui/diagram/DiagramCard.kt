package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramFile
import com.cpinto.vademo.ui.graph.Framing
import com.cpinto.vademo.ui.graph.StartAt
import com.cpinto.vademo.ui.graph.Viewport

/** The tallest a diagram is drawn inside a note; a taller one is shrunk, then cut. */
private val MAX_HEIGHT = 360.dp

/**
 * The smallest a diagram is shrunk to inside a note: box names (14 sp) at
 * about 10 sp. Shrinking without limit made a wide diagram a row of specks.
 * Past it the card shows the part where the diagram starts, faded out at
 * the cut, since the card does not pan; a tap opens all of it.
 */
internal const val CARD_READABLE_SCALE = 0.7f

private val FADE = 40.dp

/**
 * How a [width] by [height] diagram (dp) sits in a card [cardWidth] wide
 * and at most [maxHeight] tall: its [framing], the [height] of the card's
 * drawing, and which edges cut part of it off.
 */
internal data class CardFit(
    val framing: Framing,
    val height: Float,
    val cutLeft: Boolean,
    val cutRight: Boolean,
    val cutBottom: Boolean,
) {
    val cut: Boolean get() = cutLeft || cutRight || cutBottom

    companion object {
        fun of(width: Float, height: Float, cardWidth: Float, maxHeight: Float, start: StartAt): CardFit {
            val opening = Viewport.opening(width, height, cardWidth, maxHeight, CARD_READABLE_SCALE, start)
            val drawn = height * opening.scale
            // The card is as tall as the drawing when that fits, so it starts at the top rather than centred.
            val framing = if (drawn <= maxHeight) opening.copy(y = 0f) else opening
            val shown = minOf(drawn, maxHeight)
            val right = framing.x + width * framing.scale
            return CardFit(framing, shown, framing.x < -0.5f, right > cardWidth + 0.5f, framing.y + drawn > shown + 0.5f)
        }
    }
}

/**
 * A diagram shown inside a note, where the note writes `![[name]]`, with its
 * title: the whole diagram shrunk to the note's width, or, when that would
 * be too small to read, the part where it starts. It does not pan or zoom,
 * so scrolling the note never catches on it; a tap opens it on its own
 * screen. [file] is null when the knowledge base has no diagram of that name.
 */
@Composable
fun DiagramCard(name: String, file: DiagramFile?, onOpen: () -> Unit) {
    val diagram = file?.diagram
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(if (diagram != null) Modifier.clickable(onClickLabel = "Open diagram", onClick = onOpen) else Modifier)
            .semantics { contentDescription = "Diagram: ${file?.title ?: name}" },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                when {
                    file == null -> "No diagram named $name"
                    diagram == null -> "The diagram $name could not be read: ${file.error}"
                    else -> file.title
                },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (diagram == null || diagram.nodes.isEmpty()) return@Column
            val places = rememberPlaces(diagram)
            val colours = diagramColours()
            val paper = MaterialTheme.colorScheme.surfaceContainerLowest
            val start = if (diagram.direction == Diagram.Direction.TB) StartAt.Top else StartAt.TopLeft
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val fit = CardFit.of(places.width.toFloat(), places.height.toFloat(), maxWidth.value, MAX_HEIGHT.value, start)
                Column {
                    // The shrunk boxes are a picture here; the card's description names the diagram.
                    Box(Modifier.fillMaxWidth().height(fit.height.dp).clipToBounds().clearAndSetSemantics {}) {
                        Box(
                            Modifier
                                .wrapContentSize(Alignment.TopStart, unbounded = true)
                                .size(places.width.dp, places.height.dp)
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = fit.framing.x.dp.toPx()
                                    translationY = fit.framing.y.dp.toPx()
                                    scaleX = fit.framing.scale
                                    scaleY = fit.framing.scale
                                },
                        ) {
                            // No note links here: the whole card is one tap target.
                            DiagramContent(diagram, places, colours, notes = emptyMap(), actions = null)
                        }
                        if (fit.cutLeft) Fade(Alignment.CenterStart, Brush.horizontalGradient(listOf(paper, Color.Transparent)))
                        if (fit.cutRight) Fade(Alignment.CenterEnd, Brush.horizontalGradient(listOf(Color.Transparent, paper)))
                        if (fit.cutBottom) Fade(Alignment.BottomCenter, Brush.verticalGradient(listOf(Color.Transparent, paper)))
                    }
                    if (fit.cut) {
                        Text(
                            "Part of the diagram. Tap to open all of it.",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** A band at the [edge] where the drawing is cut, fading it into the card. */
@Composable
private fun BoxScope.Fade(edge: Alignment, brush: Brush) {
    val band = if (edge == Alignment.BottomCenter) Modifier.fillMaxWidth().height(FADE) else Modifier.fillMaxHeight().width(FADE)
    Box(Modifier.align(edge).then(band).background(brush))
}
