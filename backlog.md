# vademo Backlog

### 2026.11.22 (UX review: open on a picture, readable diagrams)

Asked for on 2026-11-22: "when opening the application, I would like to
see a diagram, not just the list of notes", without having to search for
notes, and diagrams whose letters can all be read. Sorted by impact, then
effort. Paths are under `app/src/main/java/com/cpinto/vademo/ui/`.

#### Opening on a picture, finding notes without search

- [x] Open a knowledge base on a picture, not on lists. Today Home's row
  opens `BrowseScreen` on its Folders tab (`browse/BrowseScreen.kt:81`);
  the map and the diagrams are two unlabelled icons in its app bar. Make
  the knowledge base's first screen its main diagram when it has one
  (the one `index.md` embeds with `![[...]]`, else the only one), and the
  map of notes otherwise, with a bottom `NavigationBar` of Map, Diagrams
  and Notes (today's tabs) that remembers the last one per knowledge
  base. HIGH, effort M.
- [x] Make the map of notes a way in, not a hairball. `GraphScreen` draws
  every note, sources included, as same-sized 140×52 dp boxes and starts
  shrunk to fit (`graph/GraphScreen.kt:37`, `graph/PanZoom.kt:39`). Start
  instead at the knowledge base's shape: `index` and the topic hubs as
  large boxes; tapping a hub opens it in place to show its notes (and a
  second tap on a note opens it), sources hidden behind a filter chip.
  That gets from opening the app to any note in two or three taps with
  no typing. HIGH, effort L.
- [x] "Show on map" from a note: the map centred on that note with only
  its links one or two steps away, so a reader can wander to related
  notes by picture. MED, effort M.
- [x] A "Continue reading" strip on the knowledge base's first screen:
  the last few notes opened, kept per knowledge base. The Recent tab
  sorts by `updated`, which is when a note was written, not read. MED,
  effort S.
- [x] Note screen: show "Links to" next to "Linked from"
  (`note/NoteScreen.kt:175`), and the note's hub as a tappable line
  under the title, so moving around never needs Back or search. MED,
  effort S.

#### Readable diagrams and map

- [x] Arrow labels collide and are tiny: 11 sp on a borderless
  `surface` pill centred on the arrow's middle, cut to two lines in
  160 dp (`diagram/DiagramDrawing.kt:404-410`), drawn over boxes and over
  each other when arrows are close. Use `labelMedium`, outline the pill,
  and slide a label along its arrow when it would cover a box or another
  label. MED, effort M.
- [x] Text cut short with no way to read the rest: group titles one line
  (`diagram/DiagramDrawing.kt:307`), box headlines and ref lines one line,
  box names three, notes four, map titles two
  (`graph/GraphScreen.kt:122`). Let group titles wrap to two lines, and
  make a tap on a box that is not a note open a bottom sheet with its
  whole name, notes and ref (today the tap does nothing). MED, effort M.
- [x] Check text contrast on coloured boxes with a unit test:
  `DiagramColours` (`diagram/DiagramDrawing.kt`) tints boxes and draws the
  headline in `onSurfaceVariant` on them, and with Material You's dynamic
  colours that pair is not checked anywhere. Test 4.5:1 for text on every
  tint and 3:1 for stripes and status borders, light and dark. The map's
  folder colours (`graph/GraphScreen.kt:136`, value 0.8 on a light
  surface) need the same check: yellows and greens fade out. MED,
  effort S.
- [x] Is the text blurry when zoomed in? Zoom is a `graphicsLayer` scale
  of the whole drawing (`graph/PanZoom.kt`), and diagrams also record
  every frame into a layer for Share as image
  (`diagram/DiagramScreen.kt`), which can draw glyphs at 1× and stretch
  them. Look at 2× and 3× on the phone; if blurry, record only while
  exporting. MED, effort S. Done in v0.29.10: it records only while
  exporting, whatever the phone shows.
- [ ] **[manual]** With v0.29.10 installed, open a diagram and zoom to 2×
  and 3×: box text and arrow labels should be as sharp as the app bar's.
  Then Share as image: the picture still shows the whole diagram, without
  the arrows' round handles.
- [x] Lines thin out with the zoom: arrows and map links are 1.5 dp
  before scaling, so about 0.5 dp at the starting scale. Keep them at
  least 1 dp on screen whatever the zoom. LOW, effort S.
- [x] Map boxes say little: one size for every note, a 2 dp folder border
  as the only colour, links without direction. Size hubs larger, fill a
  coloured header band per folder, and on tap highlight the box's links
  and neighbours before opening it. MED, effort M. Partly done in v0.26.0:
  the index, hubs and folded topics are large boxes with a 3 dp border;
  the coloured header band and the highlight on tap are still open (a tap
  now opens a folded topic, so a highlight would need a second gesture).
  Done in v0.29.9: a folder-coloured band across each box's top, and
  instead of a highlight before opening (which would cost every note an
  extra tap), the note last opened from the map is marked with its links
  when you come back to it.

#### Gestures and loading

- [x] Pan and zoom have no fling and no double tap
  (`graph/PanZoom.kt:73`): a flick stops dead, and zooming needs two
  hands on a large phone. Add fling with decay and double-tap zoom.
  MED, effort S.
- [x] Blank screen while loading: the map lays out every note before
  drawing (`graph/GraphScreen.kt:65`), and the diagram and Diagrams list
  show nothing until read (`diagram/DiagramScreen.kt:103`,
  `diagram/DiagramListScreen.kt:75`). Once a picture is the first screen
  this blank is the first thing seen: show a progress indicator. MED,
  effort S.

#### Motion and feedback (from `ux-areas-android.md`, still open)

- [x] No spinner while syncing: Home's Sync icon only greys out
  (`home/HomeScreen.kt`). Swap it for a small progress ring while any
  sync runs, and `animateContentSize` the status lines. LOW, effort S.
- [ ] Screen changes have no direction: slide in for drill-down (Home →
  knowledge base → note), and a shared-element transition from a map or
  diagram box to its note. LOW, effort M.
- [x] Lists jump: no `animateItem()` in Home's and Browse's
  `LazyColumn`s. LOW, effort S.
- [x] An empty folder shows a blank screen (`browse/BrowseScreen.kt`,
  `FolderList`): say "This folder is empty". LOW, effort S.
- [x] Saving an edit closes the editor with no word that it worked
  (`edit/EditScreen.kt:55`): a "Saved" snackbar on the note. LOW,
  effort S.
- [x] Tapping a link to a missing note gives only a snackbar: add a
  haptic tick, and on the missing-note screen a button back to the
  folder. LOW, effort S.
- [x] Long conflict sides give no sign there is more below
  (`conflict/ConflictScreen.kt`, 240 dp cap): fade the bottom edge. LOW,
  effort S.


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

- [ ] `.github/workflows/build-apk.yml`: CI run 43779009289 (2026-11-16)
  warns "The SDK Manager CLI tool (sdkmanager) is deprecated. Use Android
  CLI instead.", from `android-actions/setup-android@v4` and the "Install
  the compileSdk platform" step. The replacement is the Android CLI's
  `android sdk install` (https://developer.android.com/tools/agents/android-cli/commands/sdk).
  Blocked, checked 2026-11-16: its download page
  (https://developer.android.com/tools/agents) picks the file per visitor
  device and gives no Linux URL to script, its docs say nothing about
  licences or unattended use, and setup-android's latest release (v4.0.4)
  still uses sdkmanager. Move when either changes; sdkmanager still works.
  LOW, effort S.
- [ ] **[manual]** Measure reading every note on the phone, with v0.20.18
  installed: connect it with USB debugging, run `adb logcat -s vademo`,
  then force-stop vademo, open it and tap a knowledge base. Each line
  reads "Read N notes of <name> in X ms". Do it on the largest knowledge
  base and the public example, then put the numbers in
  `docs/kb-views-and-queries.md` (the "No database" bullet), which so far
  has only the emulator's 158 to 282 ms for the public example.

#### Refactoring


#### Test coverage

- [ ] `GitOnDeviceTest` uses only a local file remote: HTTPS with
  credentials and a conflict on a device are never tested, though the
  comment at `ReadAndEditTest.kt:33` suggests otherwise. LOW, effort L.
