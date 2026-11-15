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

### 2026.11.15 (Health tab and saved searches, v0.17.0 and v0.18.0)

- [ ] **[manual]** Check on the phone with v0.18.0 installed, on a real
  knowledge base: open it, tap the Health tab (scroll the tabs left if it
  is hidden). A pass is: the counts look right for what you know of it,
  and each row opens a list of exactly those notes; the Broken links list
  names the missing note under each one. Then in search, type a filter,
  tap the bookmark at the top, name it, clear the field: it is listed
  under Saved searches and runs again when tapped. Device tests ran on
  the emulator with a four-note fixture only.

### 2026.11.15 (/scan: security, performance, refactoring, tests)

Paths below are under `app/src/main/java/com/cpinto/vademo/` unless they
start with `app/`, `gradle`, `.github/` or `docs/`.

#### Security

- [ ] **[manual]** Decide how the APK is signed. The repository is public,
  so anyone can download `app/debug.keystore` (password `android`), sign
  their own APK as `com.cpinto.vademo`, and Android accepts it as an
  update that keeps the app's data and can decrypt the stored GitHub
  token. It needs the user to sideload that APK. The fix is a private key
  held in CI secrets (`app/build.gradle.kts:28-35`,
  `.github/workflows/build-apk.yml:101-112`), which reverses the
  committed-keystore decision in CLAUDE.md. MED, effort M.

#### Performance

- [ ] `ui/diagram/DiagramViewModel.kt:323-328`: `onCleared()` runs
  `runBlocking { save() }` on the main thread, which reads and writes the
  diagram file. Leaving a diagram right after an edit can freeze the
  screen. Save from a scope that outlives the view model. MED, effort S.
- [ ] `ui/diagram/DiagramScreen.kt:367` (`rememberPlaces`) is keyed on
  `moves`, so every drag frame re-measures every box's text and reruns
  `DiagramLayout.place`; `Edges` (:591) then reroutes every arrow.
  Remember sizes by diagram, density and styles only, and place in a
  second `remember`. HIGH while dragging, effort S.
- [ ] Every note open rebuilds `NoteIndex.of(kb)` (`ui/note/NoteScreen.kt:135`)
  just for backlinks; Browse (`ui/browse/BrowseScreen.kt:118`) and Search
  (`ui/search/SearchScreen.kt:125`) build it again, and `kb/NoteGraph.kt:16-38`
  re-reads every note with a copy of `NoteIndex`'s filter. Cache one index
  per knowledge base in `KbRepository`, drop it after sync, resolve or a
  save, and build `NoteGraph` from it. HIGH, effort M.
- [ ] `ui/diagram/DiagramViewModel.kt:110-115` (`publish`): on every edit,
  undo or redo, for each box with a `ref`, `kb.noteAt()` does file checks
  and for a bare name walks the whole tree, on the main thread. Resolve
  once per load on `Dispatchers.IO`. MED-HIGH, effort S.
- [ ] `ui/note/NoteScreen.kt:141,169`: tapping a `[[link]]` walks the whole
  knowledge base on the main thread (`kb.resolve`). Resolve against the
  index already built at :135 (`NoteIndex.note(name)`). MED, effort S.
- [ ] Search reruns in full on every keystroke with no debounce
  (`ui/search/SearchScreen.kt:99-111`), and each run rebuilds the orphan
  set and missing-links map (`kb/NoteSearch.kt:75-76`); `kb/Health.kt:24`
  pays that five times. Debounce or `mapLatest`, make those lazy on
  `NoteIndex`, store lowercased text once. MED, effort S.
- [ ] `kb/Frontmatter.kt:62`: `Regex("""\s#""")` is compiled for every
  frontmatter field of every note. Make it a `private val`. MED, effort S.
- [ ] `ui/browse/BrowseScreen.kt:219,224,271`: `recent()`, `byType()` and
  `tagCounts()` sort or group every note on each recomposition. Wrap in
  `remember(index)`. MED, effort S.
- [ ] `ui/diagram/DiagramScreen.kt:173-181,414-424`: a new `DiagramActions`
  is built on every recomposition and `DiagramContent` rebuilds `byId` and
  the depth sort, so every box recomposes on each drag frame. Remember
  both. MED, effort S/M.
- [ ] `gradle.properties`: configuration cache and build cache are off
  (Gradle 9.4.1). Turn on `org.gradle.configuration-cache` and
  `org.gradle.caching`, then check KSP and Hilt still build. MED, effort S.
- [ ] `.github/workflows/build-apk.yml:69,72`: two `./gradlew --no-daemon`
  calls each pay a JVM start and configuration; run
  `testDebugUnitTest assembleDebug` in one call. Also cache the platform
  37 SDK download (:52-66). MED, effort S.
- [ ] `data/SettingsRepository.kt:47,86-87`: every DataStore change (text
  size after a pinch, a saved search) decrypts the token again with the
  Keystore. Add `distinctUntilChanged` on the ciphertext or cache the
  plaintext; read `current()` once in `KbRepository.list()` (:49-50).
  LOW-MED, effort S.
- [ ] `data/KbRepository.kt:50-59,69-72`: knowledge bases are synced and
  inspected one after another; run them concurrently. LOW-MED, effort S.
- [ ] `kb/KnowledgeBase.kt:36-37,62-63`: `notesIn` and `summary` read whole
  files to get the frontmatter. Read up to the closing `---`, or use the
  cached index. LOW-MED, effort S.
- [ ] `ui/note/NoteScreen.kt:238-249`: `markdownTypography` and
  `markdownComponents` are rebuilt per block on every recomposition,
  including every pinch frame. Hoist into one `remember`. LOW, effort S.
- [ ] `git/GitSync.kt:147-158`: a sync walks the working tree three times
  (`status`, `add .`, `add . --update`), then `list()` runs `status`
  again. LOW, effort S.
- [ ] `docs/kb-views-and-queries.md:28-30` says reading every note is "a
  few hundred milliseconds on a phone". It is an undated estimate, never
  measured, and the index is now rebuilt on every note open, not once per
  knowledge base. Measure it on the phone and update the doc. LOW,
  effort S.

#### Refactoring

- [ ] `GitSync.conflicts()` (`git/GitSync.kt:87`) is the only public sync
  call not wrapped in `runCatching`; a broken clone throws inside
  `viewModelScope` from `ui/conflict/ConflictScreen.kt:74-76` and crashes
  the app. `KbRepository.resolve` (`data/KbRepository.kt:80`) uses
  `repoUrls.first {}`, which throws if the knowledge base was removed in
  Settings meanwhile. HIGH, effort S.
- [ ] Note editor (`ui/edit/EditScreen.kt:82-104`): `load` and `save` do
  unguarded `readText`/`writeText`, so a note deleted by a sync or a disk
  error crashes the app instead of saying so. `save()` also never
  re-checks for a conflict that appeared while the note was open (a sync
  from Home keeps running); the diagram editor does re-check at
  `ui/diagram/DiagramViewModel.kt:300`. HIGH, effort M.
- [ ] Writes are not atomic: notes and diagrams use `writeText`
  (`ui/edit/EditScreen.kt:101`, `ui/diagram/DiagramViewModel.kt:311`), so
  a half-written file can be committed by `git add .`;
  `data/DiagramMoves.kt:38-40` and `ui/diagram/DiagramShare.kt:35-37`
  write-then-rename but ignore `renameTo`'s result. One
  `File.writeAtomically` helper for all four. MED, effort S.
- [ ] `ui/diagram/DiagramScreen.kt` (683 lines) holds both the screen and
  the drawing code shared with `DiagramCard.kt` and `DiagramEditors.kt`
  (`rememberPlaces`, `diagramColours`, `DiagramContent`, `NodeBox`,
  `GroupBox`, `Edges`, `refText`). Move the drawing code to
  `ui/diagram/DiagramDrawing.kt`. MED, effort S.
- [ ] `KnowledgeBase.diagramFile(name)` exists since v0.20.3 and the
  diagram screen and note embeds use it; `createDiagram` and `diagramsIn`
  (`kb/Diagram.kt:134-147`) still build the `.tyfold-graphs` path by hand.
  Route them through it too. LOW, effort S.
- [ ] View models live in their `*Screen.kt` for eight screens but in
  their own file for Home and Diagram. Pick one convention. LOW, effort M.
- [ ] `ui/diagram/DiagramViewModel.kt:98-99`: `parse(text!!)` inside
  `runCatching` turns a missing file into "could not be read:
  NullPointerException". Check null explicitly and catch only parse
  errors; same broad catches in `kb/Diagram.kt:151` and
  `data/DiagramMoves.kt:29`. LOW, effort S.
- [ ] `DiagramViewModel.editSelected` (:197-202) exists for "edit, then end
  the selection" but `DiagramEditors.kt:86-87,100-101,292` and
  `DiagramScreen.kt:309` do it by hand. Use it. LOW, effort S.
- [ ] `ui/search/SearchScreen.kt:149-150` repeats `SearchState.offersSaved`
  (:83). Make it a function on `NoteSearch.Query`. LOW, effort S.
- [ ] Stale docs: `kb/Diagram.kt:12-13` says "vademo only reads it", and
  the "Diagram files" bullet in CLAUDE.md partly says the same; the app
  now edits diagrams. LOW, effort S.
- [ ] Dead in production: `KnowledgeBase.folders()` (`kb/KnowledgeBase.kt:27`),
  used only by a test. LOW, effort S.
- [ ] Imports: a stray `import com.cpinto.vademo.ui.BackForward` sits
  above the sorted imports in six screens (Graph, Diagram, Browse,
  DiagramList, Search, Note); `DiagramListScreen.kt:31-52` and
  `NoteScreen.kt:57` are unsorted; fully qualified names inline at
  `DiagramViewModel.kt:249,319`. LOW, effort S.
- [ ] `data/TokenCipher.kt:26-31`: if the Keystore key is lost, `decrypt`
  returns null and the token silently becomes empty; the user only sees
  the general "add your token" card. Say the stored token could not be
  read. LOW, effort S.
- [ ] `ui/graph/GraphScreen.kt:79` reads files on `Dispatchers.Default`;
  every other view model uses `Dispatchers.IO`. LOW, effort S.

#### Test coverage

- [ ] `git/GitSync.kt:148-150`: `commitLocalChanges` commits modified,
  untracked and missing files but not already-staged ones, which
  `inspect` (:74-75) does count. If the app is killed between `add` and
  `commit`, those edits are never pushed and Home shows "N edits not
  synced" for good. Regression test: stage a file, sync, expect it pushed;
  then fix. HIGH, effort S.
- [ ] Push retry (`git/GitSync.kt:166-208`) is untested: a rejected
  non-fast-forward push retried, another refusal, attempts running out,
  and "GitHub has no branch named X" (:172-173). HIGH, effort M.
- [ ] `ui/diagram/DiagramViewModel.kt:295-321` save logic has no test: the
  400 ms delay, the changed-on-disk check (`seen`), the 100-step undo
  cap, saving in `onCleared`, `deleteDiagram` cancelling a pending save.
  No view model has a JVM test because `KbRepository` needs a Context.
  HIGH, effort M.
- [ ] A conflict where one side deleted the file is untested:
  `conflicts()` returning null for `mine`/`theirs`, and `resolve` with a
  null choice (`git/GitSync.kt:87-94,107-109`). MED, effort S.
- [ ] Clone edge cases untested: a target folder with no `.git` (rename
  fails), a stale `<name>.downloading` left by a killed process
  (`git/GitSync.kt:58,130,140`). `KbRepository.downloaded()` treats any
  folder as downloaded. MED, effort S.
- [ ] `KbRepository` has no tests. Besides `resolve` above: `dirFor` gives
  `a/kb-x` and `b/kb-x` the same folder (:100-101), and once a clone
  exists sync uses `origin`, so changing a URL in Settings to another
  repository with the same name keeps syncing the old one. MED, effort M.
- [ ] `kb/Frontmatter.kt:47,54`: `withUpdated` splits with `lines()` and
  joins with `"\n"`, so saving a CRLF note rewrites every line ending and
  the next sync pushes the whole file. Test, then keep the note's line
  ending. MED, effort S.
- [ ] Settings storage untested: token encrypt/decrypt round trip, trimming
  on save, empty URL list vs missing value falling back to `DEFAULT_REPOS`
  (`data/SettingsRepository.kt:77-92`). Needs a device test for the
  Keystore. MED, effort M.
- [ ] `app/src/test/java/com/cpinto/vademo/kb/KnowledgeBaseTest.kt:76`:
  `noteAt("../outside.md")` passes only because the file does not exist;
  the canonical-path guard (`kb/KnowledgeBase.kt:52`) is never reached.
  Create the file outside the root. MED, effort S.
- [ ] `app/src/androidTest/java/com/cpinto/vademo/DiagramEditTest.kt:52,165`:
  `@Before` clears `filesDir/kb` but not `filesDir/diagram-moves`, so a
  file left by an earlier run satisfies the drag test's wait before any
  drag. MED, effort S.
- [ ] Device tests never reset the settings store
  (`ReadAndEditTest.kt:46-48,380-405`, `DiagramEditTest.kt:50-53`): saved
  Settings hide the default knowledge base, and a failed saved-search test
  leaves "Scoring notes" saved so the next run passes early. Clear it in
  `@Before`. MED, effort S.
- [ ] `kb/DiagramDocument.kt` `problems()` is partly tested; untested
  messages at :311,317-319,322-323,331-332,344,364. `ungroup` (:155) uses
  `first`, which throws on an unknown group id. LOW, effort S.
- [ ] `kb/KnowledgeBase.kt`: no direct tests of `foldersIn` on a subfolder,
  `fileAt` (including `../`), or two folders holding the same note name
  (`resolve`/`allNotes`, :43,60, keep whichever the walk reaches last).
  LOW, effort S.
- [ ] `app/src/test/java/com/cpinto/vademo/git/GitSyncTest.kt:189`:
  resolving a conflict checks only the `Synced` type, not the push, the
  merge commit message or its two parents; `names()` with more than three
  files is untested. LOW, effort S.
- [ ] `app/src/androidTest/java/com/cpinto/vademo/ReadAndEditTest.kt:110`
  checks the old `updated:` date is gone, not that it is today; :170-172
  reads `LocalDate.now()` after saving, so a run across midnight fails.
  LOW, effort S.
- [ ] `GitOnDeviceTest` uses only a local file remote: HTTPS with
  credentials and a conflict on a device are never tested, though the
  comment at `ReadAndEditTest.kt:33` suggests otherwise. LOW, effort L.
