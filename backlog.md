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
