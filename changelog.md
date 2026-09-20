## Changelog - vademo


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
