# CLAUDE.md — vademo

## Project Overview

vademo is an Android app for reading and editing markdown knowledge
bases on a phone. It keeps a git clone of each one, shows folders and
notes with their `[[links]]` tappable, lets a note be edited as plain
text, and syncs with their GitHub repositories (commit, pull, push).
When the same lines changed on the phone and on GitHub, sync stops and
the user chooses what to keep; nothing is overwritten.

The example knowledge base is
`https://github.com/carlospinto4p/kb-distributed-systems`. Its
conventions are in `.claude/rules/knowledge-base.md` in that repository;
read them before changing how notes are parsed or linked.

Package: `com.cpinto.vademo`

## Build Commands

```bash
./gradlew testDebugUnitTest          # JVM tests: parsing, links, git sync
./gradlew connectedDebugAndroidTest  # device tests (emulator or phone)
./gradlew assembleDebug              # build the debug APK
./gradlew installDebug               # install on a connected device
```

Start the emulator headless so it never takes window focus, drawing on
the real graphics card:
`~/Android/Sdk/emulator/emulator -avd Pixel_8_API_36 -no-window -no-audio -gpu host`.

Without `-gpu host` it crashes with a segmentation fault (exit 139) once
Android draws its first frames (found 2026-11-07, Fedora 44). Headless,
it falls back to the software renderer (SwiftShader), which runs the code
it generates from heap memory, and SELinux forbids that by default
(`selinuxuser_execheap` is off). `-gpu swiftshader_indirect` and
`-gpu guest` crash the same way.

For README screenshots, Android's demo mode gives a clean status bar:
`adb shell settings put global sysui_demo_allowed 1`, then
`adb shell am broadcast -a com.android.systemui.demo -e command enter`
(plus `clock -e hhmm 1000`, `notifications -e visible false`,
`battery -e level 100 -e plugged false`,
`network -e wifi show -e level 4 -e fully true`).

## Build Configuration

- Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`).
- Compile SDK 37 (required by the current AndroidX and markdown-renderer
  releases), target SDK 36, min SDK 26. Java 17.

## Architecture

- `kb/` — pure Kotlin, no Android: frontmatter, `[[link]]` rewriting,
  starting a note from a template (`NoteTemplate`),
  folder and note indexing (`NoteIndex`, every note read in one pass with
  its backlinks and orphans, in memory only; `NoteIndexCache` keeps one per
  knowledge base until a note file changes), the map of notes (`NoteGraph`,
  built from the index, and
  `GraphLayout`, which places its boxes), and diagrams in tyfold's graph
  format (`Diagram`, `DiagramLayout`, `EdgeRoute`, which bends an arrow
  round a box in its way, and `DiagramDocument`, which edits one).
  Unit-tested on the JVM.
- `git/GitSync.kt` — clone, commit, merge, push and conflict handling on
  JGit. Tested against real bare repositories in temp folders.
- `data/` — settings (DataStore, token encrypted with the Android
  Keystore) and `KbRepository`, which maps settings to local clones.
- `ui/` — Jetpack Compose + Material 3, one screen per package, Hilt
  view models.

## Decisions that are easy to undo by accident

- **JGit is pinned to 5.13.** 6.x needs Java 11 and 7.x Java 17 APIs
  that Android does not fully provide. `GitOnDeviceTest` is the proof
  that the pinned version works on a device; run it after any JGit bump.
- **A conflicting merge is left in git's merging state, not aborted.**
  That is what makes a conflict survive the app being closed. Editing is
  blocked for that knowledge base until the user chooses.
- **First download goes to `<name>.downloading` and is renamed when
  complete**, so an interrupted clone never looks like a finished one.
- **Diagram files are tyfold's format, read leniently.** tyfold owns the
  format (its `docs/GRAPH-PANE.md`, "The file") and refuses unknown keys
  when writing; vademo's reader skips unknown keys and words instead of
  failing, so a newer tyfold does not stop an older vademo. `DiagramLayout` follows tyfold's
  `src/lib/graph/canvas/layout.ts` and `EdgeRoute` its `edgeRoute.ts`;
  keep the two drawing the same file alike. One part is vademo's own for
  now: `EdgeRoute` routes an arrow that runs backwards round boxes, which
  tyfold leaves straight; when tyfold does it too, follow tyfold's. Edits go through `DiagramDocument`, which works on the file's own
  JSON so unknown keys survive, writes keys in tyfold's order, and never
  adds a `position` (tyfold keeps dragged places off the file).
- **Nothing app-private goes inside a knowledge base's folder.** Sync
  stages every file there (`git add .` in `GitSync`), so anything written
  into the clone is pushed to GitHub. Write into a clone with
  `KnowledgeBase.write`: its temporary file sits in `<name>.writing/`
  beside the clone, so a killed app leaves nothing in it.
- **`app/debug.keystore` is committed and `signingConfigs` points at
  it.** AGP otherwise signs with `~/.android/debug.keystore`, which a CI
  runner generates fresh every time, so each build came out with a
  different signature and the phone refused to install it over the
  previous one. Deleting the file, or the signing config, brings that
  back. `.gitignore` excludes `*.keystore` with an exception for it.
