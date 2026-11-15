## Changelog - vademo


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
