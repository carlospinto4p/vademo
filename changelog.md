## Changelog - vademo


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


### v0.20.9 - 16th November 2026

- Fixed every note, folder, search and map opening reading every note of
  the knowledge base again. The index of notes is now kept per knowledge
  base and read again only when a note file changed.
  - Added `kb/NoteIndexCache.kt`. Each lookup compares every note file's
    path, size and modification time with the ones the index was built
    from, so a sync, a save or a note added or deleted is always seen.
  - `KbRepository`: added `index(name)` and `indexes()`. The note, browse,
    search and map screens use them.
  - `NoteIndex`: added `files(kb)`, the files it reads.
  - `NoteGraph.of` now takes a `NoteIndex` instead of reading every note
    itself.
- Updated `docs/kb-views-and-queries.md` and CLAUDE.md to say the index is
  kept until a note changes.
- Added `NoteIndexCacheTest`: an unchanged knowledge base is not read again;
  an edit of the same size, a new note and a deleted note are noticed.


### v0.20.8 - 16th November 2026

- Fixed slow dragging in diagrams. Every frame of a drag measured the text
  of every box again. `rememberPlaces` in `ui/diagram/DiagramScreen.kt` now
  remembers the measured sizes apart from the placing, so a drag only
  places the boxes again.


### v0.20.7 - 16th November 2026

- Fixed a freeze when leaving a diagram right after an edit. The last edit
  was written on the main thread as the screen closed; it is now written in
  the background.
  - Added `data/BackgroundWrites.kt`: writes that outlive the screen that
    started them, one after another per file. Opening the diagram again
    waits for its write first, so it never shows the older version.
- Added tests:
  - `BackgroundWritesTest`: waiting for a write, writes kept in order.
  - `DiagramEditTest.leavingRightAfterAnEditStillSavesIt` (device).


### v0.20.6 - 15th November 2026

- Fixed a slow check before every save. The note editor, the diagram screen
  and the diagram list asked whether a knowledge base had a conflict by
  listing every knowledge base: a full git status of each, plus decrypting
  the token twice. The diagram autosave paid that 400 ms after every edit.
  - Added `KbRepository.needsChoice(name)`, which reads only that clone's
    merge state.
  - `GitSync.isMerging` moved to its companion object and no longer runs a
    status; it is false for a missing or damaged clone.
- Added `GitSyncTest`: `isMerging` on a clean, missing and damaged clone.


### v0.20.5 - 15th November 2026

- Updated JGit from 5.13.3 to 5.13.5, the newest release of the 5.13 line.
  5.13.4 fixed CVE-2025-4949, an XXE in repo-manifest parsing that vademo
  does not use (GitHub advisory GHSA-vrpq-qp53-qv56, checked 15th November
  2026). Unit and device tests, `GitOnDeviceTest` included, pass with it.


### v0.20.4 - 15th November 2026

- Fixed symlinks in a knowledge base reaching the app's private files. A
  symlink committed to GitHub became a real symlink on the phone (checked on
  the emulator: JGit 5.13 creates them on Android), so a note that was a link
  to an app file would be shown, and saving it would write into that file.
  - `GitSync` turns off git's `core.symlinks` in every clone, so a symlink is
    written as a plain file holding its target. Clones made before this have
    their symlinks rewritten at the next sync. Git still records them as
    symlinks, so nothing is pushed back.
  - `KnowledgeBase` skips any note, folder, index or diagram whose real
    location is outside the knowledge base, through `isInside(root, file)`.
    `fileAt` refuses such a path, and a new diagram is not written into a
    `.tyfold-graphs` that leads outside.
  - Choosing a side in a conflict replaces the file instead of writing
    through it.
- Added tests:
  - `GitSyncTest`: a symlink arrives as a plain file and the next sync sends
    nothing; an older clone's symlink is rewritten; an empty repository
    still clones.
  - `KnowledgeBaseTest`: notes, folders, the index and diagrams linked to
    files outside are skipped; a link that stays inside still works.
  - `GitOnDeviceTest`: the same symlink case on a device.


### v0.20.3 - 15th November 2026

- Fixed three ways a knowledge base from GitHub, or a link in a note, could
  reach past what it should:
  - A diagram name containing `/` or `\`, from a `![[name]]` line or a link
    to a diagram, could make the app read, overwrite or delete a `.json`
    file outside `.tyfold-graphs/`. Such a name now opens a screen saying it
    is not a diagram name, and an embed with one shows as a missing diagram.
    The check is `KnowledgeBase.diagramFile(name)`, now the one place a
    diagram's file is found.
  - A link in a note that is not to a note or a diagram is opened by
    another app only when it is `http`, `https` or `mailto`, and a link no
    app can open shows "Can't open this link" instead of closing the app.
    A relative link, a `file:` link or any other scheme gets the same
    message.
  - The GitHub token is given only to `https://github.com`. It used to go to
    whatever host a knowledge base was cloned from. `GitHubOnlyCredentials`
    checks the address JGit is about to talk to, so a clone whose `origin`
    differs from Settings is covered too.
- Added tests:
  - `KnowledgeBaseTest`: `diagramFile` refuses names that leave the folder.
  - `WikiLinksTest`: which links open in another app.
  - `GitHubOnlyCredentialsTest`: GitHub over HTTPS gets the token, other
    hosts, look-alike hosts and plain HTTP do not.


### v0.20.2 - 15th November 2026

- Added JGit rules to `app/proguard-rules.pro`, ready for when release
  builds are shrunk (`isMinifyEnabled` is still false). JGit is kept whole,
  since it fills its message texts by reflection, and R8 is told not to stop
  on the desktop Java classes JGit refers to but Android lacks (JMX,
  Kerberos, slf4j's binder). Checked by running `GitOnDeviceTest` on a
  temporarily shrunk build: clone, edit and push pass.


### v0.20.1 - 15th November 2026

- Fixed long link names breaking mid-word. A `[[link]]` such as
  `how-many-developers-run-many-coding-agents` now wraps after its dashes,
  in a paragraph and in a table cell, instead of showing "develop" on one
  line and "ers" on the next. Android does not wrap after a dash on its
  own, so `WikiLinks` puts an invisible break point after each dash in the
  text shown; the link target and the file are unchanged.
- Added tests:
  - `WikiLinksTest`: the break points are in the text, not the target.
  - `LinkLineBreakTest`: on a device, every line of a long link name ends
    at a dash or a space.


### v0.20.0 - 15th November 2026

- Back and Forward, as in a browser. Next to the back arrow on a note, a
  folder, search, the map of notes, the diagram list and a diagram there
  is now a Forward arrow. After going back, Forward opens again the screen
  just left, step by step; opening anything new forgets the way forward.
  The editor, settings and the conflict screen are never reopened by it.
- Added `ui/NavHistory.kt`: `NavHistory`, which follows the screen on top
  to tell a step back from a step somewhere new, and `BackForward`, the
  pair of buttons each of those screens now uses.
- Added tests:
  - `NavHistoryTest`: 7 tests.
  - `ReadAndEditTest.goBackThenForwardAgain`.


### v0.19.0 - 15th November 2026

- New note. Inside a folder, a New note button starts a note from one of
  the knowledge base's `templates/`: pick a template, type a title, and
  change the file name if it needs one. The file name starts as the title
  in kebab-case; a name another note in the knowledge base already has is
  refused, since `[[name]]` must lead to one note.
- The editor opens on the template with the title, today's date as
  `created` and `updated`, the `# heading`, and `Hub: [[<topic-hub>]]` set
  to the folder's hub note filled in. Every other placeholder is left as
  it is. The file is written only when saved, so going back without saving
  leaves nothing to sync.
- Added `kb/NoteTemplate.kt`: `fill()`, `fileName()` and `isValidName()`.
- `KnowledgeBase`: added `templates()` and `hubOf()`.
- The editor route takes an optional template and title
  (`Routes.newNote()`).
- Added tests:
  - `NoteTemplateTest`: 11 tests.
  - `ReadAndEditTest.startANoteFromATemplateThenSaveIt`.
  - `ReadAndEditTest.leavingANewNoteUnsavedWritesNothing`.


### v0.18.1 - 15th November 2026

- A table in a note sizes its columns by their content. The renderer gave
  every column the same width, so a long Reason column beside a one-digit
  Score came out tall and narrow. Now each column is measured, short ones
  keep their natural width, and the long ones share what is left, wrapping
  inside the screen. The table scrolls sideways only when its longest
  words do not fit.
- Added `ui/note/NoteTable.kt`, replacing the renderer's table, and
  `ui/note/TableColumns.kt` with `columnWidths()`, which shares the width
  out.
- Added tests:
  - `TableColumnsTest`: 7 tests.
  - `ReadAndEditTest.aTableGivesItsLongColumnTheWidth`, which fails on the
    old equal-width table.


### v0.18.0 - 15th November 2026

- Saved searches. Before anything is typed, the search screen lists them:
  the five Health checks first, then the user's own. Tapping one runs it,
  kept to the knowledge base the search was opened from unless it names
  one itself. The bookmark button at the top saves the current search
  under a name; the bin on a row deletes it. The Health checks cannot be
  deleted.
- A search opened from a knowledge base now starts on the saved searches
  instead of listing every one of its notes.
- Saved searches are kept in the app's settings, not in a knowledge base,
  so they never sync to GitHub.
- Added `data/SavedQueries.kt`: storing, replacing by name, deleting, and
  `applied()`, which keeps the starting `kb:` filter.
- `SettingsRepository`: added `savedQueries`, `saveQuery()` and
  `deleteQuery()`.
- Added tests:
  - `SavedQueriesTest`: 6 tests.
  - `ReadAndEditTest.saveASearchRunItAgainThenDeleteIt`.


### v0.17.0 - 15th November 2026

- A knowledge base's top screen has a fifth tab, Health. It counts the
  notes nothing links to, the notes with a link to a note that does not
  exist, the notes with an `(unverified)` marker, the open questions
  (`type: question`, `status: draft`), and the files waiting in `inbox/`.
  Tapping a row opens search with the matching filters, listing those
  notes. The tabs now scroll sideways, since five no longer fit.
- `kb/NoteSearch.kt`:
  - Added `is:broken`: notes linking to a note that does not exist. The
    results name the missing notes.
  - Added `folder:<path>`: notes inside that folder, at any depth.
  - `SearchHit.missing` lists a note's links to missing notes.
- Added `kb/Health.kt`: `SavedQuery` (a named search) and `Health.CHECKS`,
  the five rows above, with `Health.counts(index)`.
- Added tests:
  - `HealthTest`: the five counts on a knowledge base on disk.
  - `NoteSearchTest`: `is:broken` and `folder:`.
  - `ReadAndEditTest.aHealthRowListsTheNotesItCounts`.


### v0.16.0 - 15th November 2026

- The type, status and tag chips at the top of a note now do something:
  tapping one opens search in that knowledge base with the matching
  filter (`type:`, `status:` or `tag:`), listing the notes that share it.
- Removed the "decorative chips" item from
  `.claude/rules/ux-areas-android.md`, which this settles.
- Added `ReadAndEditTest.aNoteTypeChipListsTheNotesOfThatType`.


### v0.15.0 - 15th November 2026

- A knowledge base's top screen has four tabs: Folders (as before),
  Recent, Types and Tags. Recent lists the notes by their `updated:`
  date, newest first. Types groups them by `type:`, largest group first,
  with all sources as one row that opens them in search. Tags lists every
  tag with how many notes carry it; tapping one searches for it. The
  notes are read once the folders are on screen.
- A search opened with a filter already in it (from a tag or the sources
  row) no longer raises the keyboard.
- `kb/NoteIndex.kt`: added `recent()`, `byType()` and `tagCounts()`.
- Added `Routes.searchIn(kb, filters)`.
- Added tests:
  - `NoteIndexTest`: one test each for `recent`, `byType` and `tagCounts`.
  - `ReadAndEditTest`: open a note from the Types tab; open a tag from
    the Tags tab.


### v0.14.0 - 15th November 2026

- A note now ends with "Linked from (N)": the notes that link to it, each
  one tap away. Hidden when nothing links to the note. It is read after
  the note's text, so the text never waits for it.
- Added `NoteIndex.linkedFrom(path)`.
- Added tests:
  - `NoteIndexTest`: one test for `linkedFrom`.
  - `ReadAndEditTest`: open a note, then the note linking to it.


### v0.13.0 - 15th November 2026

- Search: a search button on the home screen searches every knowledge
  base, and one on a knowledge base's screen starts with `kb:<name>` to
  search only that one. Words are found in titles, headings and text,
  ranked in that order; ties go to the most recently updated note. The
  filters are `type:`, `status:`, `tag:`, `updated:` (with `>`, `>=`,
  `<`, `<=`, or a bare date matching its start), `kb:`, `links:`,
  `is:orphan` and `is:unverified`. The notes are read when the screen
  opens, so the results always match the last sync.
- Added `kb/NoteSearch.kt`: parses a query and ranks the notes of one or
  more `NoteIndex`es.
- Added `ui/search/SearchScreen.kt`, and `KbRepository.downloaded()`, the
  knowledge bases on the phone.
- Added tests:
  - `kb/NoteSearchTest.kt`: 11 tests.
  - `ReadAndEditTest`: search from the home screen and open a note;
    search one knowledge base with a filter.


### v0.12.0 - 15th November 2026

- Added `kb/NoteIndex.kt`: every note of a knowledge base read in one
  pass, in memory, never written to disk. Per note it holds the path,
  title, type, status, tags, dates, headings, outgoing links and the
  count of `(unverified)` markers. From the links it works out the
  backlinks, the links that name no note, and the orphans (notes nothing
  links to, leaving out `index.md` and hubs). Nothing shows it yet;
  search, backlinks and the health view will read from it.
- Added `kb/NoteIndexTest.kt`: 7 tests.


### v0.11.0 - 15th November 2026

- An arrow that runs backwards, against the diagram's direction, now goes
  round a box in its way instead of staying straight. It leaves its
  source by the side or the back, runs back along a free lane (a gap
  between columns of boxes, or past the outermost one, inside the
  picture), and enters its target by the side or the front, with its
  label on the lane. Of the ways that cross no box, the shortest is taken;
  with none, the arrow stays straight. tyfold leaves such an arrow
  straight, so this is vademo's own until tyfold does the same.
- `kb/EdgeRoute.kt`: a route is now a list of straight and curved pieces
  (`Piece`), so both kinds of route share one shape; the arrowhead points
  along the last straight run.
- Checked on two real diagrams: every arrow that crossed a box now
  crosses none, including the backwards "Parquet message" arrow.
- Added tests in `kb/EdgeRouteTest.kt`:
  - A backwards arrow goes round beside the column.
  - It takes the lane between two columns when only that one is free.
  - It leaves by the back, or enters by the front, when a side is taken.
  - It stays straight with no lane inside the picture, or with every way
    round taken.


### v0.10.1 - 15th November 2026

- Fixed a flaky device test: `DiagramEditTest.createADiagramFromTheList`
  failed once in three runs, because it tapped the knowledge base on the
  home screen before its row could be tapped (the row turns tappable once
  the app has found the download, after launch). Added
  `androidTest/HomeScreenSteps.kt` with `openKnowledgeBase()`, which waits
  for that, and used it in the 6 tests that opened the row straight away.


### v0.10.0 - 15th November 2026

- Arrows are kept off boxes, with tyfold's two rules (its
  `docs/GRAPH-PANE.md`):
  - In the layout (`kb/DiagramLayout.kt`, tyfold's `settle` and
    `clearRays`): a box with an arrow leaving its group is moved sideways
    until the arrow's line misses every box ahead of it, and the box at the
    arrow's other end is brought level with that line where it is free to
    move.
  - When drawing (`kb/EdgeRoute.kt`, tyfold's `edgeRoute.ts`): an arrow
    whose straight line would cross a box goes straight out of its source,
    bends once in the gap nearest its middle, and goes straight into its
    target, with its label on the bend. Worked out from where the boxes
    are, so it holds after dragging; nothing goes in the file.
- An arrow that runs backwards, against the diagram's direction, still
  stays straight and can still cross a box, as in tyfold.
- Added tests:
  - `kb/EdgeRouteTest.kt`: 7 tests.
  - `kb/DiagramLayoutTest.kt`: an arrow leaving a group misses the boxes
    ahead of it.


### v0.9.0 - 15th November 2026

- Export, as tyfold's Export does, through Android's share sheet:
  - Share as image: the whole diagram as a PNG at full size, with tyfold's
    48-unit margin and without the arrows' edit handles.
  - Share as Mermaid: the diagram as a Mermaid chart in a fenced block, so
    it renders where it is pasted. The text is tyfold's, ported line for
    line from its `src-tauri/src/graph/export.rs`, which tyfold checked by
    rendering with mermaid 12.
  - Both work while editing is off, as tyfold's export does.
- Added `kb/MermaidExport.kt` and `ui/diagram/DiagramShare.kt`; a
  FileProvider limited to the app's `cache/exports/` folder, so the share
  sheet can read the image.
- Added tests:
  - `kb/MermaidExportTest.kt`: tyfold's 4 export cases with its expected
    output, plus a box whose parent is not a group, and the fenced block.
  - `DiagramEditTest.kt`: Share as image writes a full PNG, on a device.


### v0.8.0 - 15th November 2026

- Diagrams as files: New diagram on the Diagrams list (a title and a
  direction; the file is named from the title in kebab-case, as notes
  are); Delete diagram, and Title and direction, in a diagram's menu.
- Cut, copy, paste and duplicate, with tyfold's rules: a copied group
  takes what is in it and the arrows among them; a pasted box keeps its id
  when the diagram has none like it, so a cut and a paste move it, into
  another diagram too; a copy goes into its original's group when that
  group is there. The copy is kept in the app, not on the system
  clipboard.
- Selecting several boxes: Select in a box's actions, then taps add or
  remove boxes; the top bar then groups, moves to a group, sets status or
  category, copies, cuts, duplicates or deletes them all at once.
- Dragging: a selected box can be dragged, and drags the other selected
  boxes with it; a group takes what it holds, and the groups around a box
  grow to keep holding it. Where boxes were dragged is kept on the phone
  only, in the app's own storage, never in the file or the knowledge base
  folder (tyfold's rule). Tidy, in the menu, forgets it.
- The New diagram button now has a label TalkBack reads.
- Added `data/DiagramMoves.kt`, `data/DiagramClipboard.kt`; `copy`, `cut`,
  `paste` and `duplicate` in `kb/DiagramDocument.kt`; `diagramFileName`
  and `createDiagram` in `kb/Diagram.kt`; dragged offsets in
  `DiagramLayout.place`.
- Added tests:
  - `kb/DiagramDocumentTest.kt`: 3 tests for cut, copy, paste, duplicate.
  - `kb/DiagramLayoutTest.kt`: 2 tests for dragged boxes and groups.
  - `kb/DiagramTest.kt`: 1 test for new diagram names.
  - `data/DiagramMovesTest.kt`: 2 tests.
  - `DiagramEditTest.kt`: creating, duplicating, dragging (the file stays
    unchanged) and deleting, on a device.


### v0.7.0 - 15th November 2026

- Diagrams can be edited on the phone, in tyfold's format and with
  tyfold's edits. A long press on a box or group opens its actions: edit
  its name, tag, notes, linked file and folder; connect it to another box
  with an arrow; set its status or category; move it into or out of a
  group, put it in a new group, ungroup; delete it (a group with what is
  in it). Tapping an arrow's label, or its small handle, edits the arrow:
  what flows along it, dashed or solid, reverse, delete. The menu adds a
  box or a group and edits the legend. Undo and redo in the top bar.
- Saving: each edit is written 400 ms after the last one, keys in tyfold's
  order with a two-space indent, so a phone edit to a file tyfold wrote
  changes only its own lines. Nothing is written when the file changed on
  disk since it was read (the screen reads it again and says so), when the
  diagram has a problem tyfold would refuse (named in a banner, in
  tyfold's words), or while a sync conflict waits in the knowledge base.
- Added `kb/DiagramDocument.kt`: the edits and tyfold's checks, on the
  file's own JSON, so keys vademo does not know are kept. `Diagram` now
  reads `folder`.
- Added `ui/diagram/DiagramViewModel.kt` and `DiagramEditors.kt`.
- Added tests:
  - `kb/DiagramDocumentTest.kt`: 13 tests, including a tyfold file
    written back byte for byte.
  - `ui/diagram/ParseRefTest.kt`: 2 tests.
  - `DiagramEditTest.kt`: renaming and undoing, drawing an arrow, and
    nothing saved once a conflict waits, on a device.


### v0.6.0 - 15th November 2026

- Added diagrams: the files agents draw with tyfold's graph tool into a
  knowledge base's `.tyfold-graphs/` folder, in tyfold's format. A new
  Diagrams button at the top of a knowledge base lists them; each opens on
  its own screen with drag and pinch-zoom, groups, labelled and dashed
  arrows, statuses and the legend's colours. A box whose `ref` names a note
  opens that note when tapped.
- A note line holding only `![[name]]` shows that diagram inside the note,
  shrunk to fit; a tap opens it. `![[name]]` inside a sentence becomes a
  link to it. This follows the "Diagrams" section the knowledge bases'
  `.claude/rules/knowledge-base.md` is gaining.
- Added to `kb/`:
  - `Diagram.kt`: reads the format; unknown keys and words are skipped.
  - `DiagramLayout.kt`: the file's positions when every box has one,
    otherwise tyfold's layout (each group laid out from the inside out)
    with a small layered layout in place of dagre.
  - `KnowledgeBase.noteAt()`: the note a `ref` path names.
  - `WikiLinks.parts()` and `diagramOf()`; `[[links]]` no longer match the
    inside of an `![[embed]]`.
- Added `ui/diagram/` (`DiagramListScreen`, `DiagramScreen`, `DiagramCard`);
  moved the pan and zoom and the legend to `ui/graph/PanZoom.kt`, where the
  legend now folds away with a tap.
- Added dependency `kotlinx-serialization-json` 1.13.0 and its Gradle plugin.
- Added tests:
  - `kb/DiagramTest.kt`: 6 tests.
  - `kb/DiagramLayoutTest.kt`: 8 tests.
  - `kb/WikiLinksTest.kt`: 4 tests for embeds.
  - `kb/KnowledgeBaseTest.kt`: 2 tests for `noteAt()`.
  - `ReadAndEditTest.kt`: opening a note from a diagram, and a note showing
    the diagram it embeds, on a device.


### v0.5.0 - 15th November 2026

- Added a map of notes, opened from the new button at the top of a
  knowledge base: one box per note, one line per `[[link]]`, coloured by
  folder, with drag, pinch-zoom and tap to open a note:
  - `kb/NoteGraph.kt`: the notes (the root `index.md` and every note in a
    folder) and the links between them, one line per linked pair.
  - `kb/GraphLayout.kt`: a force layout with no randomness, stretched to
    the screen's shape, then a pass that moves overlapping boxes apart.
  - `ui/graph/GraphScreen.kt`: the map screen and its folder legend.
- `kb/WikiLinks.kt`: added `targets()`, which lists the notes a text links
  to, skipping code exactly as the rendered note does.
- Added tests:
  - `kb/NoteGraphTest.kt`: 4 tests.
  - `kb/GraphLayoutTest.kt`: 5 tests.
  - `kb/WikiLinksTest.kt`: 2 tests for `targets()`.
  - `ReadAndEditTest.kt`: opening a note from the map, on a device.
- `README.md`: a "Map of notes" section.


### v0.4.2 - 7th November 2026

- `CLAUDE.md`: the headless emulator command adds `-gpu host`. Without
  it the emulator crashes as soon as Android draws, because its software
  renderer runs code from heap memory and SELinux blocks that. Also notes
  how to turn on Android's demo mode for screenshots.


### v0.4.1 - 7th November 2026

- `README.md`: three screenshots under the introduction (the example
  knowledge base after Sync, a note, and the editor), taken on the
  `Pixel_8_API_36` emulator and kept in `docs/screenshots/`.


### v0.4.0 - 7th November 2026

- A public knowledge base now syncs with no token, name or email. Only
  private repositories, and sending edits back, still need them:
  - `data/SettingsRepository.kt`: `Settings.isComplete` split into
    `canSync` (an address to clone) and `canWrite` (token, name and email).
  - `git/GitSync.kt`: an edit made with a blank name or email is refused
    with "Add your name and email in Settings to send your edits", before
    anything is staged, instead of being committed under an empty identity.
  - `ui/home/HomeViewModel.kt`, `ui/home/HomeScreen.kt`: the Sync button
    needs only a repository address; the banner became a hint about when
    setup is needed.
  - `ui/settings/SettingsScreen.kt`: the token, name and email fields say
    when they are needed.
- Added tests:
  - `data/SettingsTest.kt`: 3 tests for `canSync` and `canWrite`.
  - `git/GitSyncTest.kt`: a reader with no credentials downloads and
    pulls; an edit without a name and email is refused and stays
    uncommitted.
- `README.md`: first run starts with Sync and lists the setup as optional.


### v0.3.1 - 6th November 2026

- Added `LICENSE` (MIT) and a Licence section in `README.md`.
- Removed the names of private projects and knowledge bases, ahead of
  making the repository public:
  - `docs/kb-views-and-queries.md`: the measurements of the largest
    knowledge base stay as numbers without its name, and the measurements
    of the public example `kb-distributed-systems` are added beside them
    (28 notes, 38 KB, 186 links). The filter examples use that example.
  - `backlog.md`, `changelog.md`, `scripts/changelog-add.sh`,
    `.claude/rules/versioning.md`: private project names replaced by
    plain descriptions.


### v0.3.0 - 6th November 2026

- Changed the default knowledge base to the public example
  `kb-distributed-systems`, replacing the four private ones:
  - `data/SettingsRepository.kt`: `DEFAULT_REPOS` holds one address. A
    phone that has already saved its settings keeps its own list.
  - `data/KbRepository.kt`: `dirFor` comment uses the new example.
  - `GitSyncTest.kt`, `ReadAndEditTest.kt`: the temporary folder and the
    home-screen row are named after the new default.
- `README.md`, `CLAUDE.md`: describe the app as reading any knowledge base
  that follows the example's layout, and point at the example's own rules
  instead of a path on the author's computer.


### v0.2.7 - 22nd October 2026

- `.claude/rules/committing.md`: synced the trimmed canonical
  from the shared source (7.9k to 5.5k characters, same headings and
  instructions) to cut the instruction text loaded per session.


### v0.2.6 - 12th October 2026

- Removed `Read(**/*.conf)` from the deny list in
  `.claude/settings.json`. It blocked every `.conf` file. The three
  narrow `.conf` entries in the user-level `~/.claude/settings.json`
  still apply here.


### v0.2.5 - 28th September 2026

- Added an app icon (the app showed Android's default one): a note page
  with a small graph of linked notes, cream and amber on navy.
  - `res/mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml`:
    adaptive icon, including a `monochrome` layer so the icon follows
    Android 13+ themed icons.
  - `res/drawable/ic_launcher_foreground.xml` and
    `ic_launcher_monochrome.xml`: the vector layers.
  - `res/values/ic_launcher_background.xml`: the navy background.
  - `AndroidManifest.xml`: `android:icon` and `android:roundIcon`.


### v0.2.4 - 27th September 2026

- `.claude/rules/committing.md`/`versioning.md`: pulled the shared
  canonical trim (2026-09-27 context-budget pass) — shorter
  Concurrent-Sessions wording, one fewer commit-message example, and
  the stale PEP-735 `uv pip install -e ".[dev]"` wording fixed. No
  behavior change.


### v0.2.3 - 20th September 2026

- Fixed the signing-fingerprint step added in v0.2.2 failing the build:
  `.github/workflows/build-apk.yml` now reads the certificate with
  `apksigner`. With `minSdk` 26 the APK carries only the v2/v3
  signature, which `keytool -printcert -jarfile` cannot read, so the
  step exited 1 after a successful build and no APK was published.


### v0.2.2 - 20th September 2026

- Fixed every build being signed by a different key, which made the
  phone refuse to install one over the last one:
  - Added `app/debug.keystore` and pointed `signingConfigs` in
    `app/build.gradle.kts` at it. AGP was falling back to
    `~/.android/debug.keystore`, which a CI runner has to generate
    fresh on each run.
  - `.gitignore`: keeps ignoring `*.keystore`, with an exception for
    that one file.
  - `.github/workflows/build-apk.yml`: prints the signing certificate's
    SHA-256 after each build, so a change of key shows up in the log
    rather than on the phone.
- Updated `README.md` and `CLAUDE.md`: the committed key, and the one
  uninstall needed to move off a build made before this version.


### v0.2.1 - 20th September 2026

- Added `docs/kb-views-and-queries.md`: the design for seeing and asking
  things of a knowledge base beyond reading it note by note — an
  in-memory `NoteIndex`, the Folders/Recent/Types/Tags/Health views, one
  search field with frontmatter filters and saved queries, backlinks on
  a note, and what is deliberately left out (a graph view, a query
  language, a disk cache). Measured against the largest knowledge base: 86 notes,
  516 KB, 437 links, 47 of them sources.
- Updated `roadmap.md` and `backlog.md`: step 3 now cites that design,
  and the 2026.09.20 section itemises it in dependency order.


### v0.2.0 - 20th September 2026

- Pinch a note to change how large its text is drawn:
  - Added `data/TextZoom.kt`: the scale's default and its 0.7–2.5
    limits, and `zoomed()`/`clamp()` to apply a pinch inside them.
  - Added `ui/note/PinchTextZoom.kt`: a `Modifier` that reports
    two-finger pinches and leaves one-finger scrolling alone, rather
    than `detectTransformGestures`, which would claim the drag that
    scrolls the note.
  - `ui/note/NoteScreen.kt`: scales the font scale, not the density,
    so text reflows to the screen's width instead of running off it;
    the chips and the `Updated` date scale with the body.
  - `data/SettingsRepository.kt`: stores the scale under
    `note_text_scale`, written once per pinch rather than per frame,
    and kept out of `Settings` so saving the settings form cannot
    reset it.
- Added `app/src/test/java/com/cpinto/vademo/data/TextZoomTest.kt`:
  4 tests for the limits and for nonsense pinch factors.
- Updated `README.md`: "Reading a note" describes the gesture.


### v0.1.3 - 20th September 2026

- Added `.github/workflows/build-apk.yml`: builds the debug APK on
  every push to `main` or a `claude/**` branch, and on demand from the
  Actions tab:
  - Runs `testDebugUnitTest`, then `assembleDebug` on Temurin 17.
    `compileSdk` is 37, which the runner image does not ship; platforms
    are published per minor version now, so the step installs
    `platforms;android-37.0` and prints what the SDK does publish if
    that ever stops resolving.
  - Uploads `vademo-<version>-debug.apk` as a workflow artifact.
  - Replaces the `debug-latest` prerelease on `main` and manual runs,
    so the phone can download a plain `.apk` from Releases instead of
    a zipped artifact.
- Updated `README.md`: "Install without a computer" section — where the
  APK comes from, and that the debug key never upgrades a
  release-signed install.


### v0.1.2 - 20th September 2026

- Repointed vademo at GitHub, following the knowledge bases'
  migration off GitLab to private GitHub repos (`carlospinto4p`):
  - `data/SettingsRepository.kt`: `DEFAULT_REPOS` now lists the four
    `kb-*` repos' `github.com` clone URLs; the stored-token preference
    key renamed `gitlab_token` -> `github_token`.
  - `data/TokenCipher.kt`: keystore alias renamed
    `vademo-gitlab-token` -> `vademo-github-token`.
  - `ui/conflict/ConflictScreen.kt`: `Keep.GITLAB` renamed
    `Keep.GITHUB`; conflict-resolution copy updated.
  - `ui/settings/SettingsScreen.kt`, `ui/home/HomeScreen.kt`,
    `ui/edit/EditScreen.kt`, `ui/note/NoteScreen.kt`: UI text and the
    PAT-scope hint updated from GitLab to GitHub.
  - `README.md`, `CLAUDE.md`, `.claude/rules/ux-areas-android.md`,
    `roadmap.md`, `backlog.md`: docs/rules updated to match.
  - Comments and test fixtures in `git/GitSync.kt`, `data/KbRepository.kt`,
    `GitSyncTest.kt`, `WikiLinksTest.kt`, `ReadAndEditTest.kt` updated
    for consistency; no functional change (JGit sync is host-agnostic).


### v0.1.1 - 17th September 2026

- Rewrote `.claude/rules/ux-areas-android.md`: it was a byte-for-byte
  copy of SocratesAndroid's UX-review areas (Vico charts, workout
  actions) with nothing about vademo's own screens. Replaced with
  areas grounded in `HomeScreen`, `BrowseScreen`, `NoteScreen`,
  `EditScreen`, `ConflictScreen` and `SettingsScreen`: missing
  transitions on sync-status changes, no in-progress indicator on the
  sync button, decorative no-op chips, text-only broken-link feedback,
  no empty-folder state, no save confirmation, and no settings-form
  validation. Left `.claude/rules/review-ux-android.md` and
  `.claude/skills/review-ux-android/SKILL.md` untouched — both are
  already generic procedure, not app-specific content.


### v0.1.0 - 16th September 2026

- First version: read, edit and sync the `kb-*` knowledge bases on Android.
- Added `kb/`:
  - `FrontmatterParser`: reads the note header and refreshes `updated:` on save.
  - `WikiLinks`: rewrites `[[note]]` links into tappable links, skipping code.
  - `KnowledgeBase`: folders, notes (hub first) and link resolution.
- Added `git/GitSync.kt`: clone into a temporary folder, commit phone edits,
  merge GitLab changes, push, and stop on a conflict without overwriting.
- Added `data/`: settings with the GitLab token encrypted by the Android
  Keystore, and `KbRepository` mapping settings to local clones.
- Added screens: knowledge-base list with Sync, folders and notes, note
  reading with wrapping tables, editing, conflict choice, settings.
- Added tests:
  - `FrontmatterTest`, `WikiLinksTest`, `KnowledgeBaseTest`: 22 JVM tests.
  - `GitSyncTest`: 14 tests against real local repositories.
  - `GitOnDeviceTest`, `ReadAndEditTest`: on-device tests.
