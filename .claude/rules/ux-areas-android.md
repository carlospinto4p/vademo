# UX Areas

Project-specific UX areas to watch for when running
`/review-ux-android` or noticing issues during normal work.
vademo has no charts, no workout flows, and no REST calls — its
screens are `HomeScreen` (knowledge-base list), `KbScreen` (a
knowledge base's first screen: Map, Diagrams and Notes tabs along the
bottom), `BrowseScreen` (folders/notes, the Notes tab), `NoteScreen` (rendered markdown), `EditScreen`
(plain-text editor), `ConflictScreen` (merge resolution),
`GraphScreen` (map of notes and their links), `SearchScreen` (search
with filters, from Home or a knowledge base), `DiagramListScreen` and
`DiagramScreen` (diagrams, drawn by agents and edited on the phone through
`DiagramEditors`; also embedded in notes as `DiagramCard`), plus
`SettingsScreen`. Review against these, not a generic checklist.

## What to Look For

- **Sync status changes with no transition**: `HomeScreen`'s
  `KbRow` supporting text swaps abruptly between "Not downloaded
  yet", "Synced", "N edits not synced", and the conflict prompt —
  no `Crossfade`/`animateContentSize` softens the change. Same for
  the "Public knowledge bases sync without setup…" card, which
  appears/disappears instantly when `state.canWrite` flips.
- **No in-progress indicator during sync**: the app-bar sync
  `IconButton` only disables (`enabled = state.canSync`) while
  syncing — nothing shows *that* a sync is running except each
  row's "Syncing…" text. A small spinner on the icon itself would
  make a slow sync visible before the first row updates.
- **List reordering with no animation**: `LazyColumn`s in
  `HomeScreen` and `BrowseScreen` have no `animateItemPlacement` —
  a note appearing after a pull, or a knowledge base moving as its
  status changes, swaps in place instead of animating.
- **Screen-to-screen navigation has no transition**: Home → Browse
  → Note → Edit are plain pushes with the default abrupt swap; no
  shared-element or slide transition marks the drill-down/back
  relationship between a folder and its contents.
- **Broken-link feedback is text-only**: tapping a `[[wiki-link]]`
  to a missing note shows a Snackbar ("No note named X") with no
  haptic — easy to miss on a fast tap-and-move-on. A missing-note
  screen itself (`state.missing`) is a bare sentence with no retry
  or "back to folder" affordance beyond the app bar.
- **No empty state for an empty folder**: `BrowseScreen` renders
  nothing when a folder has no notes and no subfolders — no "This
  folder is empty" message, so it looks like a loading failure
  rather than a genuinely empty folder.
- **Edit has no save confirmation**: `EditScreen`'s check-mark
  save pops back to the previous screen with no snackbar or
  transient confirmation that the write succeeded. A failed write
  or a conflict found at save time keeps the editor open with an
  error line, so only success lacks feedback. The discard-changes
  `AlertDialog` on back is a good pattern already in place; saving
  deserves the same closure.
- **Conflict screen's long diffs have no scroll affordance**: each
  side of a conflicting file is a monospace block capped at
  `heightIn(max = 240.dp)` inside `verticalScroll` — a long file
  gives no visual cue (fade, scrollbar) that there's more below the
  fold.
- **Settings gives no save feedback**: `SettingsScreen`'s Save
  button returns to Home with no confirmation. A malformed or
  duplicate address is refused as it is added, and "On your GitHub"
  says when GitHub refuses the token, but a token that lists fine yet
  cannot push is still only found at the next sync.
- **Accessibility**: `contentDescription` is already present on the
  navigation and action icons (Sync, Settings, Back, Edit) — keep
  that going for any new icon-only control. Touch targets via
  `IconButton`/`ListItem` are already at the default 48dp; watch
  for that if a denser row layout is ever introduced.
