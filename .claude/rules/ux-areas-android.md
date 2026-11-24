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
  out at the bottom while more is below; Home's setup card ("Public
  knowledge bases sync without setup…") fades in and out with
  `animateItem()`; Settings' Save shows "Settings saved" on Home; and the
  map draws only its shape until a note is tapped, then that note's links
  with the rest faded and an Open bar. New
  screens and lists should match.
- **Screen changes slide, except a note from a box**: every screen
  change uses Material's shared-axis X motion (`AppNavHost` in
  `ui/Navigation.kt`; in from the right going in, the other way on
  Back), so keep new destinations on it rather than giving one its own.
  The one exception: a note opened from a box on the map or a diagram
  grows out of that box and shrinks back into it on Back
  (`ui/GrowFromBox.kt`). A new picture whose boxes open notes should
  record their bounds with `BoxBounds` the same way.
- **A token that cannot push is found late**: a malformed or duplicate
  address is refused as it is added, and "On your GitHub" says when
  GitHub refuses the token, but a token that lists fine yet cannot push
  is still only found at the next sync.
- **Accessibility**: `contentDescription` is already present on the
  navigation and action icons (Sync, Settings, Back, Edit) — keep
  that going for any new icon-only control. Touch targets via
  `IconButton`/`ListItem` are already at the default 48dp; watch
  for that if a denser row layout is ever introduced.
