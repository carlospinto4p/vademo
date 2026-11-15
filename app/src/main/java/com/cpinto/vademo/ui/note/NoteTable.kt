package com.cpinto.vademo.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownTableBasicText
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * A markdown table whose columns are as wide as their content needs.
 *
 * The renderer's own table gives every column the same width, so a long
 * column of reasons next to a short column of scores came out tall and
 * narrow. Here each column is measured first and [columnWidths] shares the
 * screen out; cells wrap, and the table scrolls sideways only when even its
 * longest words do not fit.
 */
@Composable
fun NoteTable(model: MarkdownComponentModel) {
    val rows = model.node.children
        .filter { it.type == GFMElementTypes.HEADER || it.type == GFMElementTypes.ROW }
        .map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    val columns = rows.maxOfOrNull { it.size } ?: 0
    if (columns == 0) return

    val dimens = LocalMarkdownDimens.current
    val annotator = annotatorSettings()
    val style = model.typography.table
    val headerStyle = style.copy(fontWeight = FontWeight.Bold)
    val line = MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(dimens.tableCornerSize)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = constraints.maxWidth
        Layout(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .clip(shape)
                .background(LocalMarkdownColors.current.tableBackground, shape),
            content = {
                rows.forEachIndexed { r, row ->
                    repeat(columns) { c ->
                        Box(
                            Modifier
                                .drawBehind {
                                    if (r < rows.lastIndex) {
                                        val y = size.height - 0.5.dp.toPx()
                                        drawLine(line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                                    }
                                }
                                .padding(dimens.tableCellPadding),
                        ) {
                            row.getOrNull(c)?.let { cell ->
                                MarkdownTableBasicText(
                                    model.content,
                                    cell,
                                    if (r == 0) headerStyle else style,
                                    Int.MAX_VALUE,
                                    TextOverflow.Clip,
                                    annotator,
                                )
                            }
                        }
                    }
                }
            },
        ) { cells, _ ->
            val grid = cells.chunked(columns)
            val widths = columnWidths(
                min = (0 until columns).map { c -> grid.maxOf { it[c].minIntrinsicWidth(Constraints.Infinity) } },
                max = (0 until columns).map { c -> grid.maxOf { it[c].maxIntrinsicWidth(Constraints.Infinity) } },
                available = available,
            )
            val heights = grid.map { row -> row.indices.maxOf { c -> row[c].maxIntrinsicHeight(widths[c]) } }
            val placeables = grid.mapIndexed { r, row ->
                row.mapIndexed { c, cell -> cell.measure(Constraints.fixed(widths[c], heights[r])) }
            }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                placeables.forEachIndexed { r, row ->
                    var x = 0
                    row.forEachIndexed { c, placeable ->
                        placeable.place(x, y)
                        x += widths[c]
                    }
                    y += heights[r]
                }
            }
        }
    }
}
