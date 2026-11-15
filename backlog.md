# vademo Backlog

### 2026.11.15 (map of notes, v0.5.0)

- [ ] **[manual]** Check the map of notes on the phone with v0.5.0
  installed: open a knowledge base, tap the map button at the top right,
  then drag, pinch, and tap a box. A pass is: every note visible at the
  start, titles readable after zooming in, the tapped note opens. Device
  tests cover the tap but not drag or pinch on real fingers.
- [ ] **Hand-drawn diagrams**, the second half of the 2026-11-15 request
  for a Svelte Flow-like feature: boxes, arrows and groups drawn by hand,
  saved as a file in the knowledge base and synced with git. tyfold's
  graph pane is the reference (its file format is in `docs/GRAPH-PANE.md`
  in that repository). Decide the file format and where diagrams live in
  the knowledge-base layout before building; drawing on a phone needs
  its own design for adding boxes and connecting them. Reuses
  `ui/graph/GraphScreen.kt`'s pan and zoom and `kb/GraphLayout.kt`.
  Effort: Large.

### 2026.09.20 (viewing and querying)

From `docs/kb-views-and-queries.md`, which has the reasoning and the
measurements behind these. In order — each needs the one before it.

- [ ] **Index a knowledge base in memory.** `kb/NoteIndex.kt`: one pass
  producing per note its path, title, type, status, tags, dates,
  headings, outgoing links and `(unverified)` count, plus the backlinks,
  unresolved links and orphans derived from them. Pure Kotlin beside
  `KnowledgeBase`, rebuilt after a sync, never written to disk. Effort:
  Medium.
- [ ] **Search across the notes** on that index: free text over title,
  headings and body, ranked in that order, with the `type:`, `status:`,
  `tag:`, `updated:`, `kb:`, `links:` and `is:` filters. Replaces the
  2026.09.16 search item, which said only "titles and text". Effort:
  Medium.
- [ ] **Show backlinks on a note** — "Linked from (N)" under the body.
  Half the link spine is unwalkable without it. Effort: Small.
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
- [ ] **Search note titles and text** across all knowledge bases. Left out
  of the first version on purpose. Effort: Medium.
- [ ] **Create a new note from a knowledge base's `templates/`** with the
  frontmatter filled in. Effort: Medium.
- [ ] **Show the health counts of a knowledge base**: `(unverified)`
  markers, notes nothing links to, broken links. Effort: Medium.
- [ ] **Add JGit keep rules before switching on release minification.**
  `isMinifyEnabled` is false; JGit uses reflection. Effort: Small.
