## Changelog - vademo


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


### v0.20.35 - 17th November 2026

- Added `SettingsStorageTest`, device tests of the settings store with
  the real Keystore: the token read back and not stored as typed,
  trimming on save, a blank token stored as none, and an empty list of
  addresses kept empty while nothing saved gives the public example.
- Added `SettingsRepository.clear()`, internal, so device tests start
  from an empty settings store.
- Device tests now reset what an earlier run left behind:
  - `ReadAndEditTest` and `DiagramEditTest` clear the settings store, so
    saved Settings no longer hide the default knowledge base and a saved
    search left by a failed run no longer lets the next run pass early.
  - `DiagramEditTest` clears `diagram-moves/`, so the drag test waits
    for this run's drag.
- `StoredSettingsTest`: added reading addresses, with no stored value,
  an empty one, and lines with spaces and blanks.
- `KnowledgeBaseTest`: `noteAt("../outside.md")` now runs with that file
  present, so the check that refuses paths outside the knowledge base is
  what returns nothing.


### v0.20.34 - 17th November 2026

- Fixed sync carrying on with the old repository after an address in
  Settings was changed to another repository with the same name. A
  knowledge base's folder is named after the last part of its address
  only, so the new address landed on the old copy. `GitSync.sync` now
  compares the copy's `origin` with the address and stops with a message
  naming the repository the copy came from. Nothing is committed, pulled
  or pushed.
- `GitSync.sameRepository` treats `https://`, `git@github.com:` and
  `file:` forms of one address alike, and ignores a final `/`, `.git`
  and letter case.
- `KbRepository`:
  - Its internal constructor takes the settings as a `suspend` function,
    so a JVM test needs no `Context`.
  - `downloaded()` lists only folders with `.git`, as `list()` already
    did; a leftover folder without one no longer counts as downloaded.
- Added `KbRepositoryTest`, its first tests: listing before and after a
  download, `downloaded()`, two addresses ending in the same name synced
  once, the regression test for a changed address, and resolving a
  knowledge base no longer in Settings.
- Added to `GitSyncTest`: the changed-address regression test, the same
  address written another way still syncs, and the address comparison.


### v0.20.33 - 17th November 2026

- `GitSync` now refuses a first download into a folder that already holds
  files but has no `.git`, before downloading anything, with a message
  naming the folder. Before, it downloaded the whole repository and then
  failed with "Could not move the download into place". The folder's
  files are left as they are.
- Added to `GitSyncTest`:
  - A half-finished download left by a killed app is replaced.
  - An empty folder in the way is downloaded into.
  - The regression test for the folder with files and no git.


### v0.20.32 - 17th November 2026

- Fixed saving a note with Windows line endings (CRLF) rewriting every
  line ending to LF, so the next sync pushed the whole file.
  `FrontmatterParser.withUpdated` now replaces only the `updated:` line
  and leaves every other byte as it was. Two regression tests in
  `FrontmatterTest`: CRLF throughout, and mixed endings.


### v0.20.31 - 16th November 2026

- Fixed the last diagram edit being lost when the diagram was closed
  within 400 ms of it. Android cancels a view model's coroutines before
  `onCleared` runs, so `DiagramViewModel` always found its pending save
  already cancelled and wrote nothing. It now keeps an `unsaved` flag,
  set when a save is scheduled and cleared once one finishes or the
  diagram is deleted.
- `KbRepository` takes its folder in an internal constructor and the
  settings as a `dagger.Lazy`, so a view model test can build one on the
  JVM without a `Context`.
- Added `DiagramViewModelTest`, the first view model test on the JVM:
  the 400 ms wait and its restart on a second edit, no write over a file
  changed on disk, the 100-step undo limit, the regression test for the
  edit written on closing, and deleting with a save still waiting.


### v0.20.30 - 16th November 2026

- Added tests to `GitSyncTest`:
  - A push rejected because GitHub moved on between fetch and push is
    merged and pushed again; rejected on all three attempts, sync gives
    up and the edit is sent by the next sync. A `pre-push` hook in the
    test clone moves the bare repository's branch to stand in for GitHub.
  - A push refused for another reason (a lock left on GitHub's side) is
    reported, not retried.
  - A branch GitHub does not have is reported by name.
  - A note deleted on one side and edited on the other stops with a
    conflict, with that side's content read as `null`; choosing the
    deletion deletes it on GitHub, choosing the text brings it back.


### v0.20.29 - 16th November 2026

- Fixed edits that were already staged never reaching GitHub. If the app
  was killed between staging and committing, sync committed only files
  modified, new or deleted since, so the staged ones stayed behind and
  Home showed "N edits not synced" for good. `GitSync` now commits staged
  files too. Regression tests in `GitSyncTest`: a staged edit and a staged
  new file, and a staged deletion.


### v0.20.28 - 16th November 2026

- Removed an unused Compose import from `GraphViewModel`.
- Dropped the backlog item asking the map of notes to read files on
  `Dispatchers.IO`: it already does, inside `KbRepository.index`. What
  runs on `Dispatchers.Default` is building and laying out the map, which
  is computation.


### v0.20.27 - 16th November 2026

- A saved GitHub token that the phone can no longer decrypt is now
  reported instead of silently read as empty:
  - `Settings.tokenUnreadable`: set when a token is stored but cannot be
    decrypted.
  - Home's setup card says the saved token can no longer be read and asks
    for it again, instead of the general "add a GitHub token" text.
  - Settings marks the token field as an error with the same message
    until a new token is typed.
- Moved reading `Settings` from storage into `settingsFrom`, which JVM
  tests can call; added `StoredSettingsTest` (3 tests).


### v0.20.26 - 16th November 2026

- Sorted the import blocks of every Kotlin file into one order (other
  packages, then `java`, `javax`, `kotlin`). Ten files had imports out of
  place, among them the `BackForward` import at the top of five screens.
- `DiagramViewModel` imports `DiagramClip` instead of naming its package
  inline.


### v0.20.25 - 16th November 2026

- Fixed the two "Java type mismatch: inferred type is 'Int?'" compiler
  warnings in `DiagramLayout`: the layering loop now ends when
  `PriorityQueue.poll()` returns null instead of checking `isNotEmpty()`
  first.
- Removed `KnowledgeBase.folders()`, which only a test called;
  `KnowledgeBaseTest` now checks the same filtering through `foldersIn`.
- Updated docs that said vademo only reads diagrams: the comment on
  `Diagram` in `kb/Diagram.kt` and the "Diagram files" bullet in
  `CLAUDE.md`.
- Fixed a nullable-receiver warning in `AtomicWriteTest`.


### v0.20.24 - 16th November 2026

- Fixed a crash on opening a diagram file that is valid JSON but not a
  diagram (for example `"nodes": 5`): `DiagramDocument.parse` now reads
  the diagram too, so the screen shows "could not be read" instead.
  Regression test in `DiagramDocumentTest`.
- Narrowed the broad catches when reading diagrams to file errors
  (`IOException`) and parse errors (`IllegalArgumentException`):
  - `DiagramViewModel`: a missing file now says "the file is missing or
    could not be opened" instead of "NullPointerException".
  - `readDiagram` in `kb/Diagram.kt`.
  - `DiagramMoves.read`.
- Added `KnowledgeBase.diagramFolder`; `createDiagram`, `diagramsIn` and
  `diagramFile` all build the `.tyfold-graphs` path from it.
- Replaced `DiagramViewModel.editSelected` with `editBoxes(ids, change)`,
  which edits and then ends the selection. The Status, Category and Move
  to group editors and the selection's Delete now use it. It takes the
  boxes as an argument because those editors also open for one box from
  its long-press menu, with nothing selected. Added two device tests in
  `DiagramEditTest` for both ways in.
- Moved every screen's view model into its own `*ViewModel.kt`, as Home
  and Diagram already had: Browse, Conflict, DiagramList, Edit, Graph,
  Note, Search and Settings, each with its state classes. No change in
  behaviour. The graph's box sizes are now `internal`, shared by
  `GraphScreen.kt` and `GraphViewModel.kt`.


### v0.20.23 - 16th November 2026

- Moved the diagram drawing shared by the diagram screen, diagrams in
  notes and the diagram editors out of `ui/diagram/DiagramScreen.kt`
  (697 lines, now 275) into `ui/diagram/DiagramDrawing.kt`:
  `DiagramActions`, `rememberPlaces`, `diagramColours`, `DiagramContent`,
  the boxes, groups and arrows, and `refText`. No change in behaviour.
  Both files keep only the imports they use, sorted.


### v0.20.22 - 16th November 2026

- Fixed `LinkLineBreakTest` failing now and then with "found 0": the
  markdown renderer (0.45.0) parses on `Dispatchers.Default`, which the
  Compose test's idle wait does not cover, so it now waits for the text
  to be drawn.


### v0.20.21 - 16th November 2026

- Fixed half-written files: every file the app writes is now written
  whole to a temporary file and moved into place in one step, so a
  killed app or a full disk leaves the old content instead of half a
  note that the next sync would push.
  - Added `kb/AtomicWrite.kt`: `File.writeAtomically`. A failed move
    now raises an error; `renameTo`'s result was ignored before.
  - Added `KnowledgeBase.write`: its temporary file sits in
    `<name>.writing/` beside the clone, never inside it.
  - Notes (`ui/edit/EditScreen.kt`), diagram saves
    (`ui/diagram/DiagramViewModel.kt`) and new diagrams
    (`createDiagram`) write through it; diagram moves
    (`data/DiagramMoves.kt`) and shared images
    (`ui/diagram/DiagramShare.kt`) through `writeAtomically`.
- Added `AtomicWriteTest`: 5 tests.


### v0.20.20 - 16th November 2026

- Fixed the note editor crashing or saving into a conflict
  (`ui/edit/EditScreen.kt`):
  - A note that cannot be read, such as one a sync removed, shows an
    error instead of crashing the app.
  - Saving checks again for a sync conflict that appeared while the note
    was open, as the diagram editor does, and refuses with the text kept
    on screen.
  - A failed write shows an error and keeps the editor open.
- Added two `ReadAndEditTest` device tests: editing a note deleted
  meanwhile, and a conflict appearing while editing.
- Updated `.claude/rules/ux-areas-android.md`: a failed save is no
  longer silent.


### v0.20.19 - 16th November 2026

- Fixed two crashes on the conflict screen:
  - `git/GitSync.kt`: `conflicts()` returns a `Result` instead of
    throwing when the clone cannot be read.
  - `ui/conflict/ConflictScreen.kt`: shows that error on the screen.
  - `data/KbRepository.kt`: `resolve` reports a knowledge base removed
    from Settings meanwhile instead of throwing.
- Added a `GitSyncTest` case: reading conflicts of a damaged or missing
  clone fails without throwing.


### v0.20.18 - 16th November 2026

- Updated `git/GitSync.kt`: a sync stages only the files its status
  found changed, instead of two more walks of the whole working tree
  (`add .` and `add . --update`). The new-and-deleted-notes test now
  also covers a note in a new folder and checks nothing is left unsynced.
- Updated `ui/note/NoteScreen.kt`: the markdown typography and
  components are built once per screen draw, not once per block.
- Added timing to reading every note:
  - `kb/NoteIndexCache.kt`: `onBuilt` hears of each index built, with
    its milliseconds.
  - `data/KbRepository.kt`: logs it, readable with
    `adb logcat -s vademo`.
  - `NoteIndexCacheTest`: a build is reported, a lookup from memory is
    not.
- Updated `docs/kb-views-and-queries.md`: the undated "a few hundred
  milliseconds" is replaced by a measurement, 158 to 282 ms for the
  public example on the Pixel 8 emulator. A phone reading is still owed.


### v0.20.17 - 16th November 2026

- Fixed folder listings reading every note to the end only to get its
  title:
  - `kb/Frontmatter.kt`: added `FrontmatterParser.read(file)`, which stops
    at the frontmatter's closing `---`.
  - `kb/KnowledgeBase.kt`: `summary`, and so `notesIn`, use it.
- Added a `FrontmatterTest` case: reading a file gives the same
  frontmatter as parsing all of it, for CRLF, missing, unclosed and empty
  frontmatter.


### v0.20.16 - 16th November 2026

- Updated `data/KbRepository.kt` so knowledge bases are synced and
  checked at the same time instead of one after another: `syncAll()`
  and `list()` run one coroutine per knowledge base. Two addresses that
  end in the same folder are synced once, so two syncs never work on
  one clone at the same time.


### v0.20.15 - 16th November 2026

- Fixed the GitHub token being decrypted with the Android Keystore on
  every settings change, such as a pinch that changes the text size or a
  saved search:
  - `data/SettingsRepository.kt`: added `DecryptOnce`, which decrypts a
    stored token once and asks again only when it changes; `settings`
    emits only when a field of `Settings` changed.
  - `data/KbRepository.kt`: `list()` reads the settings once, not twice.
- Added `DecryptOnceTest`: 3 tests.


### v0.20.14 - 16th November 2026

- Updated `.github/workflows/build-apk.yml` to action releases that run
  on Node 24, which removes CI's deprecation warnings:
  - `actions/checkout` v4 → v7.
  - `actions/setup-java` v4 → v6.
  - `actions/upload-artifact` v4 → v7.
  - `android-actions/setup-android` v3 → v4.
  - `gradle/actions/setup-gradle` v4 → v5, not v6: v6 moves its caching
    into a proprietary component under separate terms of use.


### v0.20.13 - 16th November 2026

- Removed the Actions cache of the compileSdk 37 platform from
  `.github/workflows/build-apk.yml`, added in v0.20.12. The ubuntu-26.04
  runner image already ships the platform, so the cache held 350 MB to
  restore on every run and saved nothing. The install step now says the
  platform is already installed instead of claiming a cache restore.


### v0.20.12 - 16th November 2026

- Fixed work repeated on every recomposition:
  - `BrowseScreen`: the Recent, Types and Tags tabs sort or group the
    notes once per note index, not on every recomposition.
  - `DiagramScreen`: the tap and drag handlers are kept across drag
    frames, `DiagramColours` is a data class, and the groups' drawing
    order is worked out once per diagram, so a drag recomposes only the
    boxes that moved.
- Faster builds:
  - `gradle.properties`: turned on Gradle's configuration cache and
    build cache.
  - `.github/workflows/build-apk.yml`: unit tests and the APK build run in
    one Gradle call, and the compileSdk 37 platform is kept in the
    Actions cache between runs.


### v0.20.11 - 16th November 2026

- Updated `.claude/rules/ux-areas-android.md`: the settings card on
  `HomeScreen` is named by its current text ("Public knowledge bases sync
  without setup…") and shown on `state.canWrite`, not the removed
  `settingsComplete`.


### v0.20.10 - 16th November 2026

- Fixed work done on the main thread or repeated on every keystroke or edit:
  - `DiagramViewModel`: the note each box's `ref` names is found once when
    the diagram is read, off the main thread, walking the repository at
    most once. Before, every edit, undo and redo checked the files, and a
    bare note name walked the whole repository, on the main thread. A ref
    typed in an edit is looked up off the main thread.
  - `NoteViewModel.resolve`: a tapped `[[link]]` is found in the note
    index the screen already loads. Only a note the index does not hold
    is looked for on disk, off the main thread.
  - Search runs 150 ms after typing pauses instead of on every keystroke.
    The text the screen opens with is searched at once, and the Save
    button follows the field without waiting.
  - `NoteIndex`: added `orphanNames` and `missingFrom`, built once, so a
    search and the five Health counts no longer rebuild them each time.
  - `IndexedNote`: added `titleLower`, `headingsLower` and `bodyLower`,
    lowercased once, for search's word matching.
  - `FrontmatterParser`: the comment regex is compiled once, not for every
    frontmatter field of every note. Same for the space regex in
    `NoteSearch.parse`.
- `KnowledgeBase.noteAt` takes the notes to look a bare name up in, so
  many refs share one walk.
- Added `NoteSearch.Query.asksNothing` and used it for the three copies of
  "only `kb:` filters" in the search screen.
- Added tests: `noteAt` with given notes, and `asksNothing`.
