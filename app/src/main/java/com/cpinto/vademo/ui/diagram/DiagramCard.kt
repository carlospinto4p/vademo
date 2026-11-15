package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpinto.vademo.kb.DiagramFile

/** The tallest a diagram is drawn inside a note; a taller one is shrunk to fit. */
private val MAX_HEIGHT = 360.dp

/**
 * A diagram shown inside a note, where the note writes `![[name]]`: the whole
 * diagram shrunk to the note's width, with its title. It does not pan or zoom,
 * so scrolling the note never catches on it; a tap opens it on its own screen.
 * [file] is null when the knowledge base has no diagram of that name.
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
            BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val scale = minOf(1f, maxWidth.value / places.width.toFloat(), MAX_HEIGHT.value / places.height.toFloat())
                val spare = maxWidth - (places.width * scale).dp
                // The shrunk boxes are a picture here; the card's description names the diagram.
                Box(Modifier.fillMaxWidth().height((places.height * scale).dp).clipToBounds().clearAndSetSemantics {}) {
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .size(places.width.dp, places.height.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                // Centred across the card when narrower than it.
                                translationX = (spare.toPx() / 2).coerceAtLeast(0f)
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        // No note links here: the whole card is one tap target.
                        DiagramContent(diagram, places, colours, notes = emptyMap(), actions = null)
                    }
                }
            }
        }
    }
}
