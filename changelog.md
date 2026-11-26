## Changelog - vademo


### v0.29.18 - 26th November 2026

- Updated `.github/workflows/build-apk.yml`: the `debug-latest`
  prerelease carries the APK only under its versioned name again
  (`vademo-0.29.18-debug.apk`), so each download says which build it is.
  The fixed-name `vademo-debug.apk` from v0.29.17 is gone.
- Updated `README.md`: "Install without a computer" links the release
  page (`releases/tag/debug-latest`), whose address never changes,
  instead of a file whose name changes with each version.


### v0.29.17 - 26th November 2026

- Updated `.github/workflows/build-apk.yml`: the `debug-latest`
  prerelease also carries the APK as `vademo-debug.apk`, a name that
  never changes, so one saved link always downloads the newest build.
  The versioned name (`vademo-0.29.15-debug.apk`) stopped working once
  the next build replaced it.
- Updated `README.md`: "Install without a computer" gives that link.


### v0.29.16 - 25th November 2026

- Rotated changelog: archived 26 entries to `changelog/2026.md`.


### v0.29.15 - 24th November 2026

- Updated the map of notes to draw less at once, so an open topic of
  sixty notes is no longer a tangle of every link between them:
  - `FoldedMap`: an open topic draws its hub and its `LIMIT` (12) most
    linked notes; the rest share one "N more" box (`MapBox.rest`), and
    links to them go to it. `drawn` gives the links to draw: those of the
    selected box and those that show the shape (between large boxes, or a
    box and its own topic's hub). `neighbours` gives a box's linked boxes.
    Only `index.md` and `<folder>/<folder>.md` count as hubs now, not
    every note at the root.
  - `GraphViewModel`: a tap on a note selects it and a second tap opens
    it; a tap on "N more" shows 12 more of that topic. `selected` replaces
    `lastOpened`, and a map round a note starts with it selected.
  - `GraphScreen`: only the selected note's links and the shape's are
    drawn, boxes it does not link to fade, and a bar at the top names it
    with Open and a cross; a tap on an empty spot unselects (`PanZoom`
    takes an `onTap`).
- Added 4 `FoldedMapTest` tests: the limit, the rest box, the note kept
  round a focus, and the links drawn.


### v0.29.14 - 24th November 2026

- Added a container transform from a box to its note: a note opened from
  a box on the map or a diagram grows out of that box, from the place and
  size it is drawn at, to fill the screen, and on Back shrinks into it
  again. Other screen changes still slide, among them a link opened from
  such a note and Back to it from that link.
  - `ui/GrowFromBox.kt`: `BoxBounds` follows where each box is drawn,
    read at the tap so it includes the pan and zoom; `NoteOpenings` ties
    the box tapped to the note screen that opens next, and tells going
    Back from opening a link; `Modifier.growFrom`
    draws the note shrunk to the box's width and cut to its height,
    following the screen's own enter and exit, so predictive Back plays
    it too; `GrowFrom` is its geometry.
  - `AppNavHost` (`ui/Navigation.kt`) holds the map or diagram in place
    under a note that grows or shrinks, instead of sliding either.
  - `GraphScreen` and `DiagramScreen` record the tapped box's bounds.
- Updated `PanZoom`: the framing is saved with the screen, so Back from a
  note finds the map or diagram as it was left, and the note shrinks into
  its box where it still is. It opens afresh on a screen of another size.
- Added `GrowFromBoxTest`: 13 tests of the geometry and of which note a
  tapped box goes with.
- Updated `.claude/rules/ux-areas-android.md`: the box-to-note transition
  is in place.


### v0.29.13 - 23rd November 2026

- Added "Settings saved" on Home after Save in Settings, which returned to
  Home with no word that it worked. `SettingsScreen` takes `onSaved` apart
  from `onDone` (Back), and Settings tells Home through its saved state, as
  the editor tells the note.
- Fixed Home's setup card ("Public knowledge bases sync without setup…")
  appearing and disappearing at once when a token is saved or removed: it
  is a keyed list item with `animateItem()`, like the rows below it.
- Updated `.claude/rules/ux-areas-android.md`: both are now listed as
  feedback in place.
- Tests: `ReadAndEditTest.settingsAddsAndRemovesKnowledgeBasesAsRows`
  waits for "Settings saved".


### v0.29.12 - 23rd November 2026

- Added a direction to screen changes. Going in (Home to a knowledge base,
  to a note, to its editor) the new screen slides in a little from the
  right as the old one fades out; Back slides the other way. It is
  Material's shared-axis X motion, 300 ms, in place of Navigation's
  700 ms crossfade, and is set once on `AppNavHost` in `ui/Navigation.kt`
  for every screen.
- Updated `.claude/rules/ux-areas-android.md`: screen changes slide now;
  only a shared-element transition from a box to its note is still to do.


### v0.29.11 - 23rd November 2026

- Fixed `DiagramScreen` recording the whole drawing into a layer on every
  frame, for a Share as image that is seldom used. It now records only
  while the picture is being taken. Besides the work saved at each frame
  of a pan or zoom, the screen no longer shows the drawing through that
  recording, the likely cause if diagram text looked blurry when zoomed in.
  The zoom itself is a `graphicsLayer` scale, which Android draws text
  afresh for at the new size. Not yet checked on a phone.


### v0.29.10 - 23rd November 2026

- Updated the map of notes' boxes, which said little:
  - Each box has a band of its folder's colour across its top, a larger
    patch of colour than the 2 dp border, which was the only sign of it.
  - Back on the map from a note opened there, that note's box is drawn in
    the primary colour with its links twice as wide, so where reading left
    off and where it leads show at a glance. On a map round one note, that
    note is marked the same way until another is opened.
- `GraphState.lastOpened` holds the note last opened from the map.


### v0.29.9 - 23rd November 2026

- Fixed diagram arrow labels that covered boxes and each other, and were
  small. A label now slides along its own arrow to the nearest spot where
  it covers no box and no other label, or where it covers least when no
  spot is free; it is `labelMedium` instead of `labelSmall`, on a pill with
  an outline.
- Added `kb/EdgeLabels.kt`: `place` chooses where labels go, in the file's
  order; `points` turns an arrow's route into points. `Edges` in
  `DiagramDrawing.kt` measures each label and draws it where `place` says.
- Tests: `EdgeLabelsTest`, 7 tests.


### v0.29.8 - 23rd November 2026

- Updated `.github/workflows/build-apk.yml`: Build APK also runs on pushes
  to `ccr-**` branches, the ones Claude Code cloud sessions work on. It
  ran only on `main` and `claude/**`, so a cloud session's branch had no
  check to wait for before merging into `main` unless one was started by
  hand.


### v0.29.7 - 23rd November 2026

- Updated `.claude/rules/committing.md`: "Merge Into `main` After Every
  Prompt": every prompt that changes the repository ends with its branch
  merged into `main` through a merge-commit pull request, in every session
  (local, web or the phone) and in all of the owner's repositories, replacing
  "Merge Finished Work Into Main", which merged only once a task was done.
  The post-change workflow step now says the same.


### v0.29.6 - 23rd November 2026

- Fixed diagram text cut short with no way to read the rest. Tapping a
  box or group that is not a note, which did nothing, now opens a sheet
  with its whole name, what its legend calls it, its status, its notes and
  what it refers to, and Edit when it can be edited. A group title wraps
  to two lines when the group has no note under it, which fits the room
  the layout leaves above its boxes.
- Added `DiagramDialog.Details` and `DetailsSheet`; `DiagramViewModel`
  tells with `tapOpens` whether a tap opens a box or is part of drawing an
  arrow or selecting.
- Tests: `DiagramEditTest.tapABoxThatIsNotANoteToReadAllOfIt`.


### v0.29.5 - 23rd November 2026

- Fixed lines on the map of notes and diagram arrows breaking up when
  zoomed out: drawn 1.5 dp wide before scaling, they came out near half a
  pixel on a whole map. They now stay at least 1 dp on screen whatever the
  zoom; a picture shared as an image keeps its own widths.
- Added `Viewport.lineWidth`, and `LocalZoom`, which `PanZoom` provides
  for drawing code to read.
- Tests: `ViewportTest`, `lineWidth`.


### v0.29.4 - 23rd November 2026

- A drag on the map of notes or a diagram that is let go while still
  moving now coasts on and slows to a stop, instead of stopping dead; a
  touch stops it. Double tap to zoom was already there.
- `PanZoom` follows each one-finger drag with a `VelocityTracker`, beside
  the pinch and tap gestures, and coasts with `animateDecay`; a pinch
  never flings. Added `Viewport.flings`.
- Tests: `ViewportTest`, `flings`.


### v0.29.3 - 23rd November 2026

- Motion and feedback, from the 2026-11-22 UX review:
  - Home's Sync icon becomes a progress ring while any knowledge base
    syncs (`HomeState.syncing`), and a row's status lines grow and shrink
    with `animateContentSize` instead of jumping.
  - Home's rows and a folder's rows slide with `animateItem()` when one
    comes, goes or moves.
  - An empty folder says "This folder is empty." (`BrowseState.loaded`
    tells it apart from one still being read).
  - Saving a note shows "Saved" on it: `EditScreen`'s new `onSaved` sets
    a flag in the note's saved state, which `NoteScreen` shows once.
  - A link to a missing note buzzes (`HapticFeedbackType.Reject`) as well
    as showing its snackbar, and a missing note has an "Open its folder"
    button.
  - A long version on the conflict screen fades out at the bottom while
    there is more of it below.
- Updated `.claude/rules/ux-areas-android.md`: the fixed items became one
  "keep it" bullet.


### v0.29.2 - 23rd November 2026

- Fixed colours that faded into what they sit on, on the light theme:
  legend stripes in yellow, cyan and green (2.3:1 against their box), the
  "changed" status border (2.1:1), and most of the map's folder colours
  (down to 1.4:1). Each colour is now darkened on a light theme, or
  lightened on a dark one, until it reaches 3:1 against everything behind
  it.
- Added `ui/theme/Palette.kt`: those colours as ARGB, apart from Compose,
  checked against the Material tones the theme uses whatever the
  wallpaper. `DiagramColours` and the map's `folderColours` take theirs
  from it.
- Tests: `PaletteTest`, 6 tests: text 4.5:1 on every tint, stripes,
  status borders and folder colours 3:1, every 5° of hue, light and dark.


### v0.29.1 - 23rd November 2026

- The map of notes, a diagram and the list of diagrams show a spinner
  while they are read and laid out, instead of a blank screen. Now that a
  picture is a knowledge base's first screen, that blank was the first
  thing seen.
- Added `Loading` in `ui/graph/PanZoom.kt`.


### v0.29.0 - 23rd November 2026

- Show on map, a button on a note's top bar, opens a map of that note and
  the notes up to two links from it, every one drawn, centred on the note
  and titled "Around …". Tapping one opens it.
- Added `NoteGraph.around` and `Viewport.openingAt`; `PanZoom` takes a
  `focus` point to open centred on.
- `GraphViewModel` reads a `focus` from the new `map/{kb}?focus={path}`
  route; `GraphScreen` draws that note's box in the primary colour and
  hides the chips there.
- Tests:
  - `NoteGraphTest`: `around`.
  - `ViewportTest`: `openingAt`.
  - `ReadAndEditTest.showANoteOnTheMapThenOpenANeighbour`.


### v0.28.0 - 23rd November 2026

- A "Continue reading" strip above a knowledge base's tab bar lists the
  last five notes opened there, newest first, each one tap from opening
  again. The Recent tab sorts by `updated`, when a note was written, not
  when it was read.
- Added `data/RecentNotes.kt`, kept per knowledge base in the settings by
  `SettingsRepository.recentNotes` and `noteOpened`, which
  `NoteViewModel.load` calls.
- Added `NoteIndex.noteAt`; `KbViewModel.loadRecent` drops notes that no
  longer exist.
- Tests: `RecentNotesTest`, 3 tests.


### v0.27.0 - 23rd November 2026

- A note shows "Links to" above "Linked from": the notes it links to,
  each once, in the order first linked. Above its type and tags, "In …"
  names the topic hub it sits under (the index for a hub) and opens it.
- Added `NoteIndex.linksTo` and `NoteIndex.hubOf`; `NoteState` has
  `linksTo` and `hub`, filled in with `linkedFrom`.
- `NoteScreen`'s two lists are one `NoteLinks` composable.
- Tests: `NoteIndexTest`, 2 tests for `linksTo` and `hubOf`.


### v0.26.0 - 23rd November 2026

- The map of notes starts as the knowledge base's shape instead of every
  note at once: `index.md` and one large box per topic folder with its
  note count, linked where their notes link. Tapping a topic opens it in
  place, a note or an open topic's hub opens the note, and a long press
  folds the topic again. The Every note chip opens or folds them all, and
  Sources shows the source notes, hidden at first.
- Added `kb/FoldedMap.kt`: `FoldedMap.of` and `MapBox`.
- `GraphNote` has the note's `type` and `isSource`.
- `GraphViewModel` keeps the open topics and lays the map out again on
  each change; `GraphScreen` draws large boxes for the index, hubs and
  folded topics and opens each new picture afresh.
- Tests:
  - `FoldedMapTest`: 4 tests.
  - `ReadAndEditTest.openANoteFromTheMap` opens a topic first.


### v0.25.0 - 23rd November 2026

- A knowledge base opens on a picture instead of its folders: its main
  diagram (the first one `index.md` shows with `![[name]]`, else its only
  diagram) or, with none, the map of notes. A bottom bar switches between
  Map, Diagrams and Notes (the folders and the Recent, Types, Tags and
  Health lists), and the knowledge base opens on the tab it was left on
  next time. The map and diagrams buttons left the Notes tab's top bar.
- Added `ui/kb/`:
  - `KbScreen`: the bottom bar and the tab showing, one view model per tab
    for as long as the knowledge base is open.
  - `KbViewModel`: the tab, kept in its `SavedStateHandle` and in
    `SettingsRepository.kbTab`.
  - `KbTab`, `KbStart` and `firstTab`: the tabs, and the one a knowledge
    base opens on.
- Added `mainDiagram` (`kb/MainDiagram.kt`) and `WikiLinks.embeds`.
- `HomeViewModel.open` reads the index and the diagrams before opening a
  knowledge base, at a tap and when it is reopened at launch; the `kb/{kb}`
  route replaced `browse/{kb}` at the top and `graph/{kb}`.
- On a tab, the map and the diagram list have a Search action, and a main
  diagram an All diagrams action and Search notes in its menu.
- `DiagramViewModel.load` writes an edit still waiting for its 400 ms
  delay before reading the file again, so coming back to the diagram's tab
  or from a note no longer drops it.
- Tests:
  - `MainDiagramTest`: 4 tests.
  - `KbTabTest`: 3 tests.
  - `WikiLinksTest`: `embeds`.
  - Device tests open the Notes tab, or start on the opening diagram.


### v0.24.1 - 22nd November 2026

- Added "Merge Finished Work Into Main" to `.claude/rules/committing.md`:
  each finished task is merged from its branch into `main` with a merge
  commit through a pull request, once CI is green, without asking, so a
  new session and the phone's `debug-latest` APK both see it.


### v0.24.0 - 22nd November 2026

- The app reopens the knowledge base it was left in, instead of starting
  on the list of knowledge bases each time. Going back to the list and
  leaving from there starts there next time. A knowledge base that has
  been removed, is not downloaded, is damaged or waits for a choice after
  a conflict is not reopened. Back from it still reaches the list.
- Added `SettingsRepository.lastKb` and `saveLastKb`, kept apart from
  `Settings` like the text size; `BrowseViewModel` saves it when the top
  of a knowledge base opens.
- `HomeViewModel.refresh` became `start`, which reopens the last
  knowledge base once per launch (remembered in its `SavedStateHandle`)
  and forgets it whenever the list is come back to; `kbToReopen` decides.
- Tests:
  - `ReopenTest`: 4 tests of which knowledge base is reopened.
  - `ReadAndEditTest`: `reopenTheLastKnowledgeBaseAtLaunch`, launching
    the app three times.


### v0.23.1 - 22nd November 2026

- Fixed diagrams inside a note becoming unreadable specks. `DiagramCard`
  shrank a diagram to the note's width without limit; it now stops at
  70 % (box names at about 10 sp) and shows the part where the diagram
  starts, its left side or, drawn top to bottom, its top, faded out where
  it is cut, with "Part of the diagram. Tap to open all of it." under it.
  A diagram that fits at 70 % or larger is shown whole as before.
- Added `CardFit` in `ui/diagram/DiagramCard.kt`: the card's scale, place,
  height and cut edges, apart from Compose.
- Added `CardFitTest`: 5 tests of small, readable, wide and tall diagrams.


### v0.23.0 - 22nd November 2026

- Diagrams and the map of notes open at a size that can be read. A
  drawing too big to show whole used to be shrunk to fit, down to 35 %,
  which put its 11 sp labels near 4 sp; it now opens whole only when that
  keeps it at 85 % or larger, and otherwise at 85 % where it starts: the
  top left of a left-to-right diagram, the top of a top-to-bottom one,
  the middle of the map.
- `ui/graph/PanZoom.kt`:
  - Added a Show all button at the bottom right, which turns into Zoom to
    read once all is shown. It appears only when the whole drawing is too
    small to read.
  - Added double tap: to full size round the tap, and from there back out
    to the whole drawing. Both glide over 300 ms; a drag or pinch stops
    the glide.
  - The framing is now read only while drawing, so a drag recomposes
    nothing.
- Added `ui/graph/Viewport.kt`: `Framing`, `StartAt` and the zoom
  arithmetic, apart from Compose.
- Added `ViewportTest`: 11 tests of the opening scale and place, Show
  all, zooming round a point, double tap and gliding.


### v0.22.0 - 22nd November 2026

- Settings lists the knowledge bases as rows instead of one text box of
  addresses: each shows its name, its owner (or host) and, once GitHub
  has been asked, whether it is private or public, with a button to
  remove it.
- Added "On your GitHub" to Settings: with a token, every repository it
  can reach whose name starts with `kb-`, public and private, owned,
  shared or in an organisation, each with an Add button. It is asked
  when Settings opens with a saved token, and again with the refresh
  button; a new token is asked about only when "List my kb- repositories"
  is tapped. One whose name matches a listed knowledge base cannot be
  added.
- Added "Add by address", which takes `owner/name`, `github.com/owner/name`
  or any HTTPS clone address, and says at once why one is refused (not
  HTTPS, an SSH address, already listed, or ending in a listed name)
  instead of at the next sync. Save adds an address left typed in it.
- Added to `data/`:
  - `KbAddress`: a clone address read into host, owner and name, with a
    key shared by every spelling of one repository.
  - `GitHubRepoFinder`: GitHub's `GET /user/repos`, every page, kept to
    `kb-` names.
  - `GitHubRepo`, `RepoSearch`, `HttpAnswer` and `HttpGet`.
- Tests:
  - `KbAddressTest`: 8 tests of reading, refusing and comparing addresses.
  - `GitHubRepoFinderTest`: 7 tests of filtering, paging and each failure.
  - `ReadAndEditTest`: the same-name refusal now goes through the add
    field, and adding and removing rows is saved.


### v0.21.2 - 19th November 2026

- Rotated changelog: archived 44 entries to `changelog/2026.md`.


### v0.21.1 - 17th November 2026

- Settings refuses to save two addresses that end in the same name (such
  as `a/kb-x` and `b/kb-x`), and names them under the list. Both would be
  cloned into one folder, so the second never synced and nothing said so.
  A list saved before this keeps syncing the first one only.
- Added `Settings.folderName` and `Settings.sharedFolders`; `KbRepository`
  names its folders with the first.
- Tests: `SettingsTest` for the two functions, and `ReadAndEditTest`
  "settings refuses two addresses ending in the same name" on a device.


### v0.21.0 - 17th November 2026

- Added a "Duplicate names" row to the Health tab, and the `is:duplicate`
  search filter behind it: notes whose file name another note also has.
  A `[[link]]` to such a name opens either file, and until now nothing
  said so. Listing them shows each note's path, since both titles can be
  the same.
- `NoteIndex` now reads every note file, both of a shared name included
  (`KnowledgeBase.noteFiles`); before, one of the two was left out of
  search, the map and Health. `NoteIndex.duplicateNames` holds the names.
- `HealthTest`: both files of a shared name are counted and listed.


### v0.20.36 - 17th November 2026

- Added tests where the 2026-11-15 scan found coverage missing:
  - `DiagramDocumentTest`: the check's other eight messages (an empty
    node or legend id, a ref to line 0, an empty folder, a legend longer
    than eight colours, a repeated legend or edge id, a parent that is
    not a node), and ungrouping something that is not a group, which
    leaves the diagram as it was.
  - `KnowledgeBaseTest`: `fileAt` on a path inside, `foldersIn` on a
    topic folder, and two folders holding the same note name, of which
    one is found.
  - `GitSyncTest`: resolving a conflict now checks the merge commit's
    message, author and both parents; a commit of more than three notes
    is named by how many.
- `ReadAndEditTest` checks that saving sets `updated:` (and a new note's
  `created:`) to today, and accepts the next day so a run across
  midnight passes.
