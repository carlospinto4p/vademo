# vademo Backlog

### 2026.11.15 (map of notes, v0.5.0)

- [ ] **[manual]** Check the map of notes on the phone with v0.5.0
  installed: open a knowledge base, tap the map button at the top right,
  then drag, pinch, and tap a box. A pass is: every note visible at the
  start, titles readable after zooming in, the tapped note opens. Device
  tests cover the tap but not drag or pinch on real fingers.

### 2026.11.15 (diagrams, v0.6.0)

- [ ] **[manual]** Check diagrams on the phone with v0.6.0 installed, once
  a knowledge base has one: open a knowledge base, tap the Diagrams button
  (the tree icon at the top right), open a diagram, drag and pinch, tap a
  box linked to a note. Then open a note with a `![[name]]` line: the
  diagram shows in the note, the note still scrolls over it, and a tap
  opens it. Device tests cover the taps, not real fingers.
- [ ] **[manual]** Check v0.11.0's arrows on the phone with a diagram whose
  arrows cross boxes, such as nakamoto's `price-to-model.json`. The "gRPC"
  arrow should go straight down out of its box, bend once beside the boxes
  of the models group, and go straight into its target, with its label on
  the bend. The "Parquet message" arrow should leave the top of the
  service box, run right through the gap between the rows, go up the gap
  between the storage boxes and RabbitMQ, and enter RabbitMQ from the
  left. Then drag a box into an arrow's way: the arrow goes round it. Unit
  tests check the paths, but nothing has looked at them on a screen.

### 2026.11.15 (editing diagrams on the phone, v0.7.0)

Asked for on 2026-11-15: "everything tyfold has", in three versions:
v0.7.0 (editing), v0.8.0 (files, selecting, dragging, clipboard) and
v0.9.0 (export). What is left is checking each on a phone.

- [ ] **[manual]** Check editing on the phone with v0.7.0 installed: long
  press a box (the actions sheet opens), edit its name, connect it to
  another box, tap the new arrow's handle and label it, undo twice, then
  Sync and check the change on GitHub as a one-line diff in the
  `.tyfold-graphs/` file. Device tests cover these taps, not real fingers.
- [ ] **[manual]** Check v0.8.0 on the phone: New diagram on the Diagrams
  list; in a diagram, Select a box, tap two more, drag one of them (all
  three move); Copy, open another diagram, Paste; Tidy; Delete diagram.
  Then Sync: the dragging must not show in the commit, only the pasted
  boxes.
- [ ] **[manual]** Check v0.9.0 on the phone: in a diagram's menu, Share
  as image to a chat or Drive (the picture shows the whole diagram, with no
  small round handles on the arrows), and Share as Mermaid into a GitHub
  issue comment's preview (the chart renders).

### 2026.09.20 (viewing and querying)

From `docs/kb-views-and-queries.md`, which has the reasoning and the
measurements behind these. In order — each needs the one before it.

- [ ] **Recent, Types and Tags views** of a knowledge base, beside the
  folder list. Sources are 47 of the largest knowledge base's 86 notes, so grouping
  by type is what makes a list readable. Effort: Medium.
- [ ] **Make the note's chips run a query** instead of `onClick = {}`,
  which also closes the decorative-chips item in
  `.claude/rules/ux-areas-android.md`. Effort: Small.
- [ ] **Health view per knowledge base**: orphans, broken links,
  `(unverified)` figures, open questions, waiting inbox items — each row
  opening the list behind the number. Effort: Medium.
- [ ] **Saved queries**, with the Health rows as the first ones. Effort:
  Small.

### 2026.09.16 (first version)

- [ ] **Size table columns by their content.** The markdown renderer gives
  every column the same width, so a long reasons column (a scorecard note
  in the largest knowledge base) becomes tall and narrow and needs a
  sideways swipe. Needs a custom table layout in `ui/note/NoteScreen.kt`.
  Effort: Medium.
- [ ] **Create a new note from a knowledge base's `templates/`** with the
  frontmatter filled in. Effort: Medium.
- [ ] **Show the health counts of a knowledge base**: `(unverified)`
  markers, notes nothing links to, broken links. Effort: Medium.
- [ ] **Add JGit keep rules before switching on release minification.**
  `isMinifyEnabled` is false; JGit uses reflection. Effort: Small.
