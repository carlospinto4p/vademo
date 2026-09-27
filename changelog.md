## Changelog - vademo


### v0.2.4 - 27th September 2026

- `.claude/rules/committing.md`/`versioning.md`: pulled programme's
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
  language, a disk cache). Measured against `kb-business`: 86 notes,
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

- Repointed vademo at GitHub, following the `kb-*`/`sapiary` family's
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
