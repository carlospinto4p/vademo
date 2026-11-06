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

Start the emulator headless so it never takes window focus:
`~/Android/Sdk/emulator/emulator -avd Pixel_8_API_36 -no-window -no-audio`.

## Build Configuration

- Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`).
- Compile SDK 37 (required by the current AndroidX and markdown-renderer
  releases), target SDK 36, min SDK 26. Java 17.

## Architecture

- `kb/` — pure Kotlin, no Android: frontmatter, `[[link]]` rewriting,
  folder and note indexing. Unit-tested on the JVM.
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
- **`app/debug.keystore` is committed and `signingConfigs` points at
  it.** AGP otherwise signs with `~/.android/debug.keystore`, which a CI
  runner generates fresh every time, so each build came out with a
  different signature and the phone refused to install it over the
  previous one. Deleting the file, or the signing config, brings that
  back. `.gitignore` excludes `*.keystore` with an exception for it.
