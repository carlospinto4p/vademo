# UX Areas

Project-specific UX areas to watch for when running
`/review-ux-android` or noticing issues during normal work.
vademo has no charts, no workout flows, and no REST calls — its
screens are `HomeScreen` (knowledge-base list), `BrowseScreen`
(folders/notes), `NoteScreen` (rendered markdown), `EditScreen`
(plain-text editor) and `ConflictScreen` (merge resolution), plus
`SettingsScreen`. Review against these, not a generic checklist.

## What to Look For

- **Sync status changes with no transition**: `HomeScreen`'s
  `KbRow` supporting text swaps abruptly between "Not downloaded
  yet", "Synced", "N edits not synced", and the conflict prompt —
  no `Crossfade`/`animateContentSize` softens the change. Same for
  the "Add your GitHub token…" card, which appears/disappears
  instantly when `settingsComplete` flips.
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
- **Decorative chips that look tappable**: `NoteScreen`'s
  frontmatter `AssistChip`s (type, status, tags) have an empty
  `onClick = {}` — they render with a ripple but do nothing. Either
  give them a real action (e.g. filter notes by tag) or render them
  as plain, non-interactive labels so they stop implying a tap does
  something.
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
  transient confirmation that the write succeeded — worth adding
  given a failed write (disk, permissions) would look identical to
  a successful one from the user's side. The discard-changes
  `AlertDialog` on back is a good pattern already in place; saving
  deserves the same closure.
- **Conflict screen's long diffs have no scroll affordance**: each
  side of a conflicting file is a monospace block capped at
  `heightIn(max = 240.dp)` inside `verticalScroll` — a long file
  gives no visual cue (fade, scrollbar) that there's more below the
  fold.
- **Settings gives no save feedback**: `SettingsScreen`'s Save
  button returns to Home unconditionally; an invalid token or
  malformed repo URL is only discovered later, at the next sync
  attempt, with no immediate validation or error state on the form
  itself.
- **Accessibility**: `contentDescription` is already present on the
  navigation and action icons (Sync, Settings, Back, Edit) — keep
  that going for any new icon-only control. Touch targets via
  `IconButton`/`ListItem` are already at the default 48dp; watch
  for that if a denser row layout is ever introduced.
