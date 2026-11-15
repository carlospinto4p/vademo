package com.cpinto.vademo.ui.diagram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cpinto.vademo.kb.Diagram
import com.cpinto.vademo.kb.DiagramDocument
import com.cpinto.vademo.kb.DiagramEdge
import com.cpinto.vademo.kb.DiagramNode
import com.cpinto.vademo.kb.NodeFields
import com.cpinto.vademo.kb.Ref

/** Which editor is open over the diagram. */
sealed interface DiagramDialog {
    data class Actions(val id: String) : DiagramDialog
    data class EditNode(val id: String) : DiagramDialog
    data class Status(val id: String) : DiagramDialog
    data class Category(val id: String) : DiagramDialog
    data class MoveTo(val id: String) : DiagramDialog
    data class Edge(val id: String) : DiagramDialog
    data object Legend : DiagramDialog
}

/** Opens the editor [dialog] names; [onChange] opens another, or closes with null. */
@Composable
internal fun DiagramDialogs(dialog: DiagramDialog, viewModel: DiagramViewModel, onChange: (DiagramDialog?) -> Unit) {
    val close = { onChange(null) }
    // What the editor was opened on may be gone (undone, or the file read again): close it.
    val doc = viewModel.document ?: return CloseNow(close)
    when (dialog) {
        is DiagramDialog.Actions -> ActionsSheet(dialog.id, doc, viewModel, onChange)
        is DiagramDialog.EditNode -> {
            val fields = doc.fieldsOf(dialog.id) ?: return CloseNow(close)
            NodeEditor(fields, isGroup = doc.diagram.nodes.any { it.id == dialog.id && it.isGroup }, onDismiss = close) {
                viewModel.edit { d -> d.editNode(dialog.id, it) }
                close()
            }
        }
        is DiagramDialog.Status -> Choice(
            title = "Status",
            options = listOf<DiagramNode.Status?>(null) + DiagramNode.Status.entries,
            label = { it?.name?.lowercase() ?: "None" },
            selected = doc.fieldsOf(dialog.id)?.status,
            onDismiss = close,
        ) { status ->
            viewModel.edit { it.setStatus(setOf(dialog.id), status) }
            close()
        }
        is DiagramDialog.Category -> CategoryChooser(dialog.id, doc, viewModel, close)
        is DiagramDialog.MoveTo -> {
            val current = doc.diagram.nodes.firstOrNull { it.id == dialog.id }?.parent
            Choice(
                title = "Move to group",
                options = listOf<DiagramNode?>(null) + doc.groupsFor(setOf(dialog.id)),
                label = { it?.text ?: "No group" },
                selected = doc.diagram.nodes.firstOrNull { it.id == current },
                onDismiss = close,
            ) { group ->
                viewModel.edit { it.moveToGroup(setOf(dialog.id), group?.id) }
                close()
            }
        }
        is DiagramDialog.Edge -> {
            val edge = doc.diagram.edges.firstOrNull { it.id == dialog.id } ?: return CloseNow(close)
            EdgeEditor(edge, doc, viewModel, close)
        }
        DiagramDialog.Legend -> LegendEditor(doc, viewModel, close)
    }
}

@Composable
private fun CloseNow(close: () -> Unit) {
    LaunchedEffect(Unit) { close() }
}

/** What can be done to one box or group, from a long press on it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsSheet(id: String, doc: DiagramDocument, viewModel: DiagramViewModel, onChange: (DiagramDialog?) -> Unit) {
    val node = doc.diagram.nodes.firstOrNull { it.id == id } ?: return CloseNow { onChange(null) }
    val holds = doc.diagram.nodes.any { it.parent == id }
    ModalBottomSheet(onDismissRequest = { onChange(null) }) {
        Text(node.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        HorizontalDivider()
        val row = @Composable { text: String, action: () -> Unit ->
            ListItem(headlineContent = { Text(text) }, modifier = Modifier.clickable(onClick = action))
        }
        row("Edit") { onChange(DiagramDialog.EditNode(id)) }
        row("Connect to…") {
            viewModel.startConnecting(id)
            onChange(null)
        }
        row("Status…") { onChange(DiagramDialog.Status(id)) }
        row("Category…") { onChange(DiagramDialog.Category(id)) }
        row("Move to group…") { onChange(DiagramDialog.MoveTo(id)) }
        row("Put in a new group") {
            val group = viewModel.editReturning { it.groupAround(setOf(id)) }
            onChange(group?.let { DiagramDialog.EditNode(it) })
        }
        if (node.isGroup) {
            row("Add a box inside") {
                val added = viewModel.editReturning { it.addNode(group = false, parent = id) }
                onChange(added?.let { DiagramDialog.EditNode(it) })
            }
            row("Ungroup") {
                viewModel.edit { it.ungroup(id) }
                onChange(null)
            }
        }
        row(if (node.isGroup && holds) "Delete the group and what is in it" else "Delete") {
            viewModel.edit { it.removeNodes(setOf(id)) }
            onChange(null)
        }
        Box(Modifier.padding(bottom = 24.dp))
    }
}

/** `path` or `path:line`, as tyfold's editor reads the linked-file field. */
internal fun parseRef(text: String): Ref? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val line = trimmed.substringAfterLast(':', "").toIntOrNull()
    return if (line != null) Ref(trimmed.substringBeforeLast(':'), line) else Ref(trimmed, null)
}

@Composable
private fun NodeEditor(fields: NodeFields, isGroup: Boolean, onDismiss: () -> Unit, onSave: (NodeFields) -> Unit) {
    var label by remember { mutableStateOf(fields.label) }
    var tag by remember { mutableStateOf(fields.tag.orEmpty()) }
    var notes by remember { mutableStateOf(fields.notes.orEmpty()) }
    var ref by remember { mutableStateOf(fields.ref?.let(::refText).orEmpty()) }
    var folder by remember { mutableStateOf(fields.folder.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGroup) "Edit group" else "Edit box") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(tag, { tag = it }, label = { Text("Tag, such as service or database") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2)
                OutlinedTextField(
                    ref,
                    { ref = it },
                    label = { Text("Linked file or note") },
                    supportingText = { Text("A path from the repository root, such as consensus/raft.md; add :line for a line.") },
                    singleLine = true,
                )
                OutlinedTextField(
                    folder,
                    { folder = it },
                    label = { Text("Folder where its work is done") },
                    supportingText = { Text("tyfold shows the session working there.") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(fields.copy(label = label.trim(), tag = tag, notes = notes, ref = parseRef(ref), folder = folder))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One choice out of [options], applied as soon as it is picked. */
@Composable
private fun <T> Choice(
    title: String,
    options: List<T>,
    label: (T) -> String,
    selected: T,
    onDismiss: () -> Unit,
    leading: @Composable (T) -> Unit = {},
    footer: @Composable () -> Unit = {},
    onPick: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onPick(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        leading(option)
                        Text(label(option), Modifier.padding(start = 8.dp))
                    }
                }
                footer()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun LegendDot(colour: Color) {
    Box(Modifier.padding(start = 8.dp).size(12.dp).background(colour, CircleShape))
}

/** The legend's entries to choose from, plus a field to add one, as tyfold's "New category…". */
@Composable
private fun CategoryChooser(id: String, doc: DiagramDocument, viewModel: DiagramViewModel, close: () -> Unit) {
    val diagram = doc.diagram
    val colours = diagramColours()
    var fresh by remember { mutableStateOf("") }
    val full = diagram.legend.size >= Diagram.LEGEND_HUES.size
    Choice(
        title = "Category",
        options = listOf<String?>(null) + diagram.legend.map { it.id },
        label = { entry -> diagram.legend.firstOrNull { it.id == entry }?.label ?: "None" },
        selected = doc.fieldsOf(id)?.category,
        onDismiss = close,
        leading = { entry -> diagram.hueOf(entry)?.let { LegendDot(colours.stripe(it)) } },
        footer = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    fresh,
                    { fresh = it },
                    label = { Text(if (full) "The legend has all eight colours" else "New category") },
                    enabled = !full,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = !full && fresh.isNotBlank(), onClick = {
                    viewModel.edit { d -> d.addLegendEntry(fresh)?.let { (added, entry) -> added.setCategory(setOf(id), entry) } ?: d }
                    close()
                }) { Text("Add") }
            }
        },
    ) { entry ->
        viewModel.edit { it.setCategory(setOf(id), entry) }
        close()
    }
}

@Composable
private fun EdgeEditor(edge: DiagramEdge, doc: DiagramDocument, viewModel: DiagramViewModel, close: () -> Unit) {
    val names = doc.diagram.nodes.associate { it.id to it.text }
    var label by remember { mutableStateOf(edge.label.orEmpty()) }
    var dashed by remember { mutableStateOf(edge.style == DiagramEdge.Style.DASHED) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("${names[edge.source] ?: edge.source} → ${names[edge.target] ?: edge.target}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("What flows along it") }, singleLine = true)
                Row(Modifier.clickable { dashed = !dashed }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dashed, onCheckedChange = null)
                    Text("Dashed", Modifier.padding(start = 8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.edit { it.reverseEdge(edge.id) }
                        close()
                    }) { Text("Reverse") }
                    TextButton(onClick = {
                        viewModel.edit { it.removeEdge(edge.id) }
                        close()
                    }) { Text("Delete") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.edit { it.editEdge(edge.id, label, if (dashed) DiagramEdge.Style.DASHED else DiagramEdge.Style.SOLID) }
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}

/**
 * Renames, removes and adds legend entries, applied together as one undo step
 * when saved. Removing an entry takes its colour off every box, and the
 * entries after it move to the earlier colours, as in tyfold.
 */
@Composable
private fun LegendEditor(doc: DiagramDocument, viewModel: DiagramViewModel, close: () -> Unit) {
    val diagram = doc.diagram
    val colours = diagramColours()
    val labels = remember { mutableStateListOf(*diagram.legend.map { it.label }.toTypedArray()) }
    val removed = remember { mutableStateListOf<String>() }
    var fresh by remember { mutableStateOf("") }
    val kept = diagram.legend.indices.filter { diagram.legend[it].id !in removed }
    val full = kept.size >= Diagram.LEGEND_HUES.size
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Legend") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Each colour means one thing about the boxes, such as owner or layer. At most eight.",
                    style = MaterialTheme.typography.bodySmall,
                )
                kept.forEachIndexed { place, i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(colours.stripe(Diagram.LEGEND_HUES[place % Diagram.LEGEND_HUES.size]))
                        OutlinedTextField(
                            labels[i],
                            { labels[i] = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        IconButton(onClick = { removed += diagram.legend[i].id }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Remove ${labels[i]}")
                        }
                    }
                }
                OutlinedTextField(
                    fresh,
                    { fresh = it },
                    label = { Text(if (full) "The legend has all eight colours" else "Add an entry") },
                    enabled = !full,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.edit { start ->
                    var d = start
                    diagram.legend.forEachIndexed { i, entry ->
                        d = if (entry.id in removed) d.removeLegendEntry(entry.id) else d.renameLegendEntry(entry.id, labels[i])
                    }
                    if (fresh.isNotBlank()) d = d.addLegendEntry(fresh)?.first ?: d
                    d
                }
                close()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
    )
}
