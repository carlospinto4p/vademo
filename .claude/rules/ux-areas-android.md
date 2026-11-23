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

- **Feedback already in place, keep it**: Home's Sync icon turns
  into a progress ring while any sync runs and each row's status
  lines grow and shrink with `animateContentSize`; Home's and
  Browse's rows use `animateItem()`; an empty folder says so; a save
  shows "Saved" on the note; a link to a missing note buzzes
  (`HapticFeedbackType.Reject`) as well as showing a snackbar, and a
  missing note offers "Open its folder"; a long conflict version fades
  out at the bottom while more is below. New screens and lists should
  match.
- **The "Public knowledge bases sync without setup…" card** on Home
  still appears and disappears instantly when `state.canWrite`
  flips.
- **Screen-to-screen navigation has no transition**: Home → a
  knowledge base → Note → Edit are plain pushes with the default
  abrupt swap; no shared-element or slide transition marks the
  drill-down/back relationship between a folder and its contents.
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
